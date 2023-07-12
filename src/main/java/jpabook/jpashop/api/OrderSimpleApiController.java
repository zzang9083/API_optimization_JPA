package jpabook.jpashop.api;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.OrderRepository;
import jpabook.jpashop.repository.OrderSearch;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * x To One(ManyToOne, OneToOne)
 * Order
 * Order -> Member
 * Order -> Delivery
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository;


    /**
     * V1    : 주문을 조회하는 API
     *
     * 문제점 1 : 양방향 관계가 있는 객체간의 무한접근이 일어나서 무한루프가 돈다
     *
     *   SOL 1: 양방향 걸려있는 애들을 다 JsonIgnore 에너테이션 처리 -> 또 에러난다. why? 지연로딩 설정된 객체와 같은 경우 프록시객체로 생성하는데 jsonIgnore 때문에 객체가 로딩이 안되니까
     *                                                                           문제가 발생
     *   SOL 2: Hibernate5Module를 사용해서 적절하게 Lazy Loading 하도록한다.
     *
     * 문제점 2: 원치않는 필드 데이터까지 다 나오게 된다.
     *          Order 객체만 원했는데 양방향 관계가 있는 다른 객체들까지 다 나온다.
     *
     * */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> orderV1() {
        List<Order> all = orderRepository.findAllByString(new OrderSearch());
        //강제 지연 로딩 설정 안하고서 나오도록
        for(Order order : all) {
            order.getMember().getName();      // Lazy 강제 초기화
            order.getDelivery().getAddress(); // Lazy 강제 초기화
        }
        return all;
    }

    /**
     *
     * V2    : 응답으로 나갈 엔티티를 API용으로 별도 생성하여, 내부 객체와 매핑했다.
     *
     * 개선점 : 응답으로 나갈 필드만 명시적으로 나갈 수 있음.
     *
     * 문제점 : n+1 문제 - 터치되는만큼 쿼리가 계속 수행됨(영속성 컨텍스트에 없으면 쿼리로 수행)
     *
     * */
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> orderV2() {
        // order 2개가 조회
        List<Order> orders = orderRepository.findAllByString(new OrderSearch());

        // dto에 조회되는 oneToN의 객체들에 대해 쿼리가 루프를 돌면서 실행(터치되는만큼 쿼리가 계속 수행됨)
        List<SimpleOrderDto> result = orders.stream()
                                            .map(o -> new SimpleOrderDto(o))
                                            .collect(Collectors.toList());

        return result;

    }

    /**
     *
     * V3    : fetch join으로 쿼리 최적화
     *
     * 개선점 : nTo1 로 일어나는 n+1 문제에 대해서 fetch join으로 해결(가지고 올 객체를 fetch join)
     *
     * 문제점 : 페치 조인으로 조회시 안 가지고 와도 되는 필드요소들까지 다 가지고 온다.
     *
     * */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> orderV3() {
        List<SimpleOrderDto> orders = orderRepository.findAllWithMemberDelivery()
                .stream().map(SimpleOrderDto::new).collect(Collectors.toList());

        return orders;
    }

    /**
     *
     * V4    : select 필드 최적화
     *
     * 개선점 : select 해오는 필드에 대한 dto를 만들어서 비용을 줄인다.
     *
     * ISSUE : V3 : 재사용성이 높다.(전부 다 가지고 오니까..)
     *         V4 : 원하는 것만 가지고 오긴하지만, 해당 질의에 대한 역할밖에 못한다. - 성능상으로 조금 더 낫다.(솔직히 성능차이 별로 안난다)
     *
     *         -> 요청 트래픽에 따라 어떤 것으로 사용할지 결정하는게 좋을지 판단 필요.
     *
     *         -> 전역적으로 쓰이지않고, 이런식으로 (특수성 있는 용도)로 쓰이는 쿼리는 repository 영역에서 별도로 분리한다.
     *
     ***쿼리 방식 선택 권장순서
     *  1. 우선 엔티티를 DTO로 변환하는 방법을 선택한다.(V2)
     *  2. 필요하면 패치 조인으로 성능을 최적화 한다 -> 대부분 성능 이슈가 해결.(V3)
     *  3. 그래도 안되면 DTO를 직접 만들어서 직접 조회하는 방법을 선택한다.(V4)
     *
     * */
    @GetMapping("/api/v3/simple-orders")
    public List<OrderSimpleQueryDto> orderV4() {
        //return orderRepository.findOrderDtos();
        return orderSimpleQueryRepository.findOrderDtos();
    }

    @Data
    static class SimpleOrderDto {
        private Long orderId;
        private String name;
        private LocalDateTime orderDate;
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
        }
    }


}
