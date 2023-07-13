package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * V1    : 주문리스트를 조회하는 API
     *
     * 문제점 : 조회하고자 하는 정보가 아니더라도 '모든' 정보가 API 응답으로 노출된다.
     *         응답 엔티티 필드를 바꾸면, 응답 스펙이 바뀌어버린다.
     *
     *  SOL 1: 노출 안하려고 하는 필드를 JsonIgnore 에너테이션 처리 - 이 필드를 다른 곳에서 쓴다면? 답이 없다.
     *
     *
     * */
    @GetMapping("api/v1/orders")
    public List<Order> orderV1() {
        List<Order> all = orderRepository.findAllByString((new OrderSearch()));
        // lazy 로딩되는 것들을 터치시킴(hibernate5Module을 통해 터치되는 것들만 세팅됨)
        for(Order order : all) {
            order.getMember().getName();
            order.getDelivery().getAddress();

            //oneToMany인게 어떻게될까?
            List<OrderItem> orderItems = order.getOrderItems();
            orderItems.stream().forEach(o -> o.getItem().getName());
        }

        return all;

    }

    /**
     *
     * V2    : 응답으로 나갈 엔티티를 API용으로 별도 생성하여, 내부 객체와 매핑했다.
     *
     * 개선점 : 응답으로 나갈 필드만 명시적으로 나갈 수 있음.
     *
     * 문제점 : 1. List<OrderItem> 데이터가 외부에 다 노출됨.. 클라이언트에 줄 데이터만 표시하도록 dto를 또 만들어야한다.(출력 해줄거만 해주면 되는데 터치안된 null값들까지 다 나옴)
     *            sol1 -> List로 나가는 OrderItem 조차도 dto로 만들어줘야한다.
     *         2. 각 객체로 접근하는 sql이 어마어마하게 많이 나온다.(List가 collection이니까 더 나오겠지..)
     * */
    @GetMapping("api/v2/orders")
    public List<OrderDto> orderV2() {
        List<Order> orders = orderRepository.findAllByString((new OrderSearch()));


        return orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
    }

    /**
     *
     * V3    : fetch join으로 쿼리 최적화
     *
     * 개선점 : 1Ton 로 일어나는 n+1 문제에 대해서 fetch join으로 해결(가지고 올 객체를 fetch join)
     *
     * 문제점1 : 1개에 n개의 데이터를 갖고 오게되므로 쿼리 결과 데이터가 원치않게 뻥튀기가 되서 나올 수 있다.
     *          sol ->  jpql에 distinct를 붙여주어 구분할 객체에 distinct를 해준다.
     * 문제점2 : 1:N FETCH JOIN일 경우, 페이징 하면 안된다 : db에서는 distinct 전에 데이터를 기준으로 데이터를 페이징하는데 1TON 페치 조인일 경우
     *          데이터가 우리가 생각했던 것과 다르게 뻥튀기되므로 페이징이 원활히 되지않는다.
     *
     *   한계  : 1:N FETCH JOIN은 하나 이상 사용하면 안된다. 데이터의 부정합이 올 수 있다.
     *
     * */
    @GetMapping("api/v3/orders")
    public List<OrderDto> orderV3() {
        List<Order> orders = orderRepository.findAllWithItem();


        return orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
    }

    /**
     *
     * V3.1    : 쿼리 최적화
     *
     * 개선점 : 1Ton 관계의 페치조인에서 페이징을 할 경우, distinct 전에 페이징을 하는 문제로 데이터의 부정합이 올 수도 있다.
     *         이에 NTo1에 대해서만 패치조인하고, 1ToN은 lazy fetch으로 남겨둔다. 이때 쿼리가 lazy fetch로 넘어가는 것을 대비해서
     *         application.yml에 default_batch_fetch_size 설정을 통해 lazy fetch를 최적화한다.(인쿼리 방식으로 최적화)
     *         - 페치 조인 방식과 비교해서 쿼리 호출 수가 약간 증가하지만, DB 데이터 전송량이 감소한다.
     *         - 무엇보다 컬렉션 페치조인은 페이징이 불가능 하지만 이 방법은 페이징이 가능하다.
     * 알아둘 것 : default_batch_fetch_size는 100-1000개 사이로 적절하게 설정한다.
     *
     * */
    @GetMapping("api/v3.1/orders")
    public List<OrderDto> orderV3_page(
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "100") int limit)
    {
        List<Order> orders = orderRepository.findAllWithMemberDelivery(); //ToOne 관계가 걸린거만 패치조인으로 가지고오는 method

        return orders.stream()
                .map(o -> new OrderDto(o))
                .collect(Collectors.toList());
    }

    /**
     * JPA에서 DTO 직접 조회
     *
     * 방식 : NTO1은 원 쿼리에 1TON는 분리하여 함수화하여 루프 쿼리를 돌린다.
     *
     * 문제 : n+1문제 발생
     *
     * */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() { //원래 엔티티의 레이아웃이 아니라 엔티티 값들을 입맛대로 DTO 형태로 만듦
        return orderQueryRepository.findOrderQueryDtos();
    }

    /**
     * JPA에서 DTO 직접 조회
     *
     * 개선 : ordersV4에서 발생한 N+1 문제 해결
     *
     *
     * */
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() { //원래 엔티티의 레이아웃이 아니라 엔티티 값들을 입맛대로 DTO 형태로 만듦
        return orderQueryRepository.findAllByDto_optimization();
    }

    @Data
    static class OrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;

        private OrderStatus orderStatus;
        private Address address;

        //private List<OrderItem> orderItems;
        private List<OrderItemDto> orderItems;


        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getMember().getAddress();
            orderItems = order.getOrderItems().stream()
                    .map(orderItems -> new OrderItemDto(orderItems))
                    .collect(Collectors.toList());
            //프록시 초기화(안하면 밑에꺼가 null 나올거임 - orderItems가 엔티티라서 null...)
            //order.getOrderItems().stream().forEach(o -> o.getItem().getName());
            //orderItems = order.getOrderItems();
        }
    }

    @Data
    static class OrderItemDto {
        private String itemName;    // 상품명
        private int orderPrice;     // 상품 가격
        private int count;          // 주문 수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }

    }
}
