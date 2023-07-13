package jpabook.jpashop.repository.order.query;

import jpabook.jpashop.domain.Order;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OrderQueryRepository {

    private final EntityManager em;


    /**
     * 컬렉션은 별도로 조회
     * Query: 루트 1번, 컬렉션 N 번
     * 단건 조회에서 많이 사용하는 방식
     *
     * 해당 쿼리도 root의 결과만큼 n번의 추가 쿼리가 돌므로 n+1 문제가 발생한다.
     */
    public List<OrderQueryDto> findOrderQueryDtos() { // NTo1에서 묶은거 + 1ToN로 따로 뺀거
                                                      // 왜 나누었는가? TO1관계는 조인하면 ROW가 증가 안하고, ToN 관계는 조인하면 row수가 증가한다.
        //루트 조회(toOne 코드를 모두 한번에 조회) -> 단건객체들
        List<OrderQueryDto> result = findOrders();

        //루프를 돌면서 컬렉션 추가(추가 쿼리 실행) -> List
        result.forEach(o -> {
            List<OrderItemQueryDto> orderItems = finOrderItems(o.getOrderId());
            o.setOrderItems(orderItems);
        });
        return result;

    }

    /**
     * 1:N 관계(컬렉션)를 제외한 나머지를 한번에 조회
     */
    private List<OrderQueryDto> findOrders() { // NTo1에 있는 엔티티들은 먼저 묶어서 처리
        return em.createQuery(
                        "select new jpabook.jpashop.repository.order.query.OrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                                // , List<OrderItemQueryDto> orderItems : 컬렉션을 바로 넣기는 어려워서 제외한다. 가져올 수 있을만큼 함수화해서 상위 메소드를 하나 더 만든다.
                                // select 값을 바로 가지고 와야하기 때문에 fetch join이 아니고 걍 join
                                " from Order o" +
                                " join o.member m" +
                                " join o.delivery d", OrderQueryDto.class)
                .getResultList();

    }

    /**
     * 1:N 관계인 orderItems 조회
     */
    private List<OrderItemQueryDto> finOrderItems(Long orderId) { //1ToN에 있는 엔티티들은 따로 뺴서 처리
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                        "from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id = :orderId",OrderItemQueryDto.class)
        .setParameter("orderId", orderId)
        .getResultList();
    }

    public List<OrderQueryDto> findAllByDto_optimization() {
        List<OrderQueryDto> result = findOrders();

        //orderId 결과가 n개가 있을거다.
        List<Long> orderIds = result.stream()
                                .map(o -> o.getOrderId())
                                .collect(Collectors.toList());

        List<OrderItemQueryDto> orderItems = em.createQuery(
                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                        " from OrderItem oi" +
                        " join oi.item i" +
                        " where oi.order.id in :orderIds",OrderItemQueryDto.class) // '= :orderId'이 'in :orderIds'로 바뀌었다.
        .setParameter("orderIds", orderIds)
        .getResultList();

        // orderItemQueryDto를 OrderId로 그룹바이하여 map으로 저장
        Map<Long, List<OrderItemQueryDto>> orderItemMap  = orderItems.stream()
                .collect(Collectors.groupingBy((orderItemQueryDto -> orderItemQueryDto.getOrderId())));

        // 메모리상에서 result에 값을 세팅
        result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));

        return result;
    }

    public List<OrderFlatDto> findAllByDto_flat() {
        return em.createQuery(
                        "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
                         " from Order o" +
                        " join o.member m" +
                        " join o.delivery d" +
                        " join o.orderItems oi " +
                        " join oi.item i", OrderFlatDto.class)
                .getResultList();
    }



//    /**
//     * 컬렉션은 별도로 조회
//     * Query: 루트 1번, 컬렉션 N 번
//     * 단건 조회에서 많이 사용하는 방식
//     */
//    public List<OrderQueryDto> findOrderQueryDtos() {
//        //루트 조회(toOne 코드를 모두 한번에 조회)
//        List<OrderQueryDto> result = findOrders();
//
//        //루프를 돌면서 컬렉션 추가(추가 쿼리 실행)
//        result.forEach(o -> {
//            List<OrderItemQueryDto> orderItems = findOrderItems(o.getOrderId());
//            o.setOrderItems(orderItems);
//        });
//        return result;
//    }
//
//    /**
//     * 1:N 관계(컬렉션)를 제외한 나머지를 한번에 조회
//     */
//    private List<OrderQueryDto> findOrders() {
//        return em.createQuery(
//                "select new jpabook.jpashop.repository.order.query.OrderQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
//                        " from Order o" +
//                        " join o.member m" +
//                        " join o.delivery d", OrderQueryDto.class)
//                .getResultList();
//    }
//
//    /**
//     * 1:N 관계인 orderItems 조회
//     */
//    private List<OrderItemQueryDto> findOrderItems(Long orderId) {
//        return em.createQuery(
//                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
//                        " from OrderItem oi" +
//                        " join oi.item i" +
//                        " where oi.order.id = : orderId", OrderItemQueryDto.class)
//                .setParameter("orderId", orderId)
//                .getResultList();
//    }
//
//    /**
//     * 최적화
//     * Query: 루트 1번, 컬렉션 1번
//     * 데이터를 한꺼번에 처리할 때 많이 사용하는 방식
//     *
//     */
//    public List<OrderQueryDto> findAllByDto_optimization() {
//
//        //루트 조회(toOne 코드를 모두 한번에 조회)
//        List<OrderQueryDto> result = findOrders();
//
//        //orderItem 컬렉션을 MAP 한방에 조회
//        Map<Long, List<OrderItemQueryDto>> orderItemMap = findOrderItemMap(toOrderIds(result));
//
//        //루프를 돌면서 컬렉션 추가(추가 쿼리 실행X)
//        result.forEach(o -> o.setOrderItems(orderItemMap.get(o.getOrderId())));
//
//        return result;
//    }
//
//    private List<Long> toOrderIds(List<OrderQueryDto> result) {
//        return result.stream()
//                .map(o -> o.getOrderId())
//                .collect(Collectors.toList());
//    }
//
//    private Map<Long, List<OrderItemQueryDto>> findOrderItemMap(List<Long> orderIds) {
//        List<OrderItemQueryDto> orderItems = em.createQuery(
//                "select new jpabook.jpashop.repository.order.query.OrderItemQueryDto(oi.order.id, i.name, oi.orderPrice, oi.count)" +
//                        " from OrderItem oi" +
//                        " join oi.item i" +
//                        " where oi.order.id in :orderIds", OrderItemQueryDto.class)
//                .setParameter("orderIds", orderIds)
//                .getResultList();
//
//        return orderItems.stream()
//                .collect(Collectors.groupingBy(OrderItemQueryDto::getOrderId));
//    }
//
//    public List<OrderFlatDto> findAllByDto_flat() {
//        return em.createQuery(
//                "select new jpabook.jpashop.repository.order.query.OrderFlatDto(o.id, m.name, o.orderDate, o.status, d.address, i.name, oi.orderPrice, oi.count)" +
//                        " from Order o" +
//                        " join o.member m" +
//                        " join o.delivery d" +
//                        " join o.orderItems oi" +
//                        " join oi.item i", OrderFlatDto.class)
//                .getResultList();
//    }
}