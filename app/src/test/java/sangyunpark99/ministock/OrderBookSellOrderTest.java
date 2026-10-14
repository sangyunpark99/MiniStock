package sangyunpark99.ministock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sangyunpark99.ministock.engine.OrderBook;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("호가창 매도 주문 (order)")
class OrderBookSellOrderTest {

    private static final String SYMBOL = "000660";

    private OrderBook orderBook;

    @BeforeEach
    void setUp() {
        orderBook = new OrderBook();
    }

    private Order buy(String orderId, long price, int count) {
        return new Order(SYMBOL, OrderType.BUY, price, count, orderId);
    }

    private Order sell(String orderId, long price, int count) {
        return new Order(SYMBOL, OrderType.SELL, price, count, orderId);
    }

    @SuppressWarnings("unchecked")
    private <T> T field(String name) {
        try {
            Field f = OrderBook.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(orderBook);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private TreeMap<Long, List<Order>> book(String name) {
        return field(name);
    }

    private Map<String, Order> orderById() {
        return field("orderById");
    }

    /** 준비용: 매수 호가와 orderById에 주문을 쌓는다 */
    private void restBid(Order order) {
        book("bids").computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
        orderById().put(order.getOrderId(), order);
    }

    private List<Order> asksAt(long price) {
        return book("asks").getOrDefault(price, List.of());
    }

    private List<Order> bidsAt(long price) {
        return book("bids").getOrDefault(price, List.of());
    }

    @Test
    @DisplayName("매수 호가가 없으면 매도 주문은 매도 호가에 쌓인다")
    void noBids() {
        Order order = sell("S-1", 10_000, 5);

        orderBook.order(order);

        assertEquals(List.of(order), asksAt(10_000));
        // orderById
        assertSame(order, orderById().get("S-1"));
        assertEquals(1, orderById().size());
    }

    @Test
    @DisplayName("매수 호가가 없을 때 같은 가격 매도가 두 번 오면 순서대로 쌓인다")
    void samePriceTwice() {
        Order first = sell("S-1", 10_000, 3);
        Order second = sell("S-2", 10_000, 4);

        orderBook.order(first);
        orderBook.order(second);

        assertEquals(List.of(first, second), asksAt(10_000));
        // orderById
        assertSame(first, orderById().get("S-1"));
        assertSame(second, orderById().get("S-2"));
        assertEquals(2, orderById().size());
    }

    @Test
    @DisplayName("최우선 매수 호가가 내 가격보다 싸면 체결 없이 매도 호가에 쌓인다")
    void bidTooCheap() {
        Order bid = buy("B-1", 9_900, 5);
        restBid(bid);
        Order order = sell("S-1", 10_000, 5);

        orderBook.order(order);

        assertEquals(5, bid.getCount());
        assertEquals(List.of(bid), bidsAt(9_900));
        assertEquals(List.of(order), asksAt(10_000));
        // orderById: 둘 다 호가창에 남아 있으므로 둘 다 존재
        assertSame(bid, orderById().get("B-1"));
        assertSame(order, orderById().get("S-1"));
        assertEquals(2, orderById().size());
    }

    @Test
    @DisplayName("가격과 수량이 같으면 전량 체결되고 양쪽 호가에서 사라진다")
    void fullMatch() {
        Order bid = buy("B-1", 10_050, 3);
        restBid(bid);
        Order order = sell("S-1", 10_050, 3);

        orderBook.order(order);

        assertEquals(0, order.getCount());
        assertEquals(0, bid.getCount());
        assertFalse(book("bids").containsKey(10_050L));
        assertFalse(book("asks").containsKey(10_050L));
        // orderById: 둘 다 전량 체결 → 둘 다 없어야 함
        assertNull(orderById().get("B-1"));
        assertNull(orderById().get("S-1"));
        assertTrue(orderById().isEmpty());
    }

    @Test
    @DisplayName("같은 가격의 매수 주문들과 순서대로 체결되고, 상대의 남은 수량은 맨 앞에 남는다")
    void partialFillWithinPriceLevel() {
        Order b = buy("B-B", 10_050, 3);
        Order c = buy("B-C", 10_050, 4);
        restBid(b);
        restBid(c);
        Order order = sell("S-1", 10_000, 6);

        orderBook.order(order);

        assertEquals(0, order.getCount());       // 6주 모두 체결
        assertEquals(0, b.getCount());           // B 3주 체결
        assertEquals(1, c.getCount());           // C 3주 체결, 1주 남음
        assertEquals(List.of(c), bidsAt(10_050));
        assertFalse(book("asks").containsKey(10_000L));
        // orderById: B, 매도는 사라지고 C만 남음
        assertNull(orderById().get("B-B"));
        assertSame(c, orderById().get("B-C"));
        assertNull(orderById().get("S-1"));
        assertEquals(1, orderById().size());
    }

    @Test
    @DisplayName("여러 매수 호가가 있으면 가장 비싼 가격부터 체결한다")
    void highestBidFirst() {
        Order cheap = buy("B-1", 9_900, 5);     // 싼 호가를 먼저 넣어도
        Order expensive = buy("B-2", 10_100, 5);
        restBid(cheap);
        restBid(expensive);
        Order order = sell("S-1", 9_900, 5);

        orderBook.order(order);

        assertEquals(0, expensive.getCount());   // 비싼 호가가 먼저 체결
        assertFalse(book("bids").containsKey(10_100L));
        assertEquals(5, cheap.getCount());       // 싼 호가는 그대로
        assertEquals(List.of(cheap), bidsAt(9_900));
        // orderById: 비싼 매수와 매도는 사라지고, 싼 매수만 남음
        assertNull(orderById().get("B-2"));
        assertSame(cheap, orderById().get("B-1"));
        assertNull(orderById().get("S-1"));
        assertEquals(1, orderById().size());
    }

    @Test
    @DisplayName("한 가격 레벨을 다 소진하면 다음(더 싼) 가격 레벨로 넘어가 체결한다")
    void sweepMultiplePriceLevels() {
        restBid(buy("B-1", 10_050, 3));
        Order next = buy("B-2", 10_000, 5);
        restBid(next);
        Order order = sell("S-1", 10_000, 6);

        orderBook.order(order);

        assertEquals(0, order.getCount());
        assertFalse(book("bids").containsKey(10_050L));   // 빈 가격 레벨 제거
        assertEquals(2, next.getCount());
        assertEquals(List.of(next), bidsAt(10_000));
        // orderById: B-1, 매도는 사라지고 B-2만 남음
        assertNull(orderById().get("B-1"));
        assertSame(next, orderById().get("B-2"));
        assertNull(orderById().get("S-1"));
        assertEquals(1, orderById().size());
    }

    @Test
    @DisplayName("내 가격보다 싼 호가에서 멈추고, 남은 수량은 매도 호가에 쌓인다")
    void stopAtLimitPriceAndRestRemainder() {
        restBid(buy("B-1", 10_050, 3));
        Order cheap = buy("B-2", 9_900, 5);
        restBid(cheap);
        Order order = sell("S-1", 10_000, 6);

        orderBook.order(order);

        assertEquals(3, order.getCount());               // 3주 체결, 3주 남음
        assertFalse(book("bids").containsKey(10_050L));
        assertEquals(5, cheap.getCount());               // 싼 호가는 그대로
        assertEquals(List.of(order), asksAt(10_000));
        // orderById: B-1은 사라지고, B-2와 남은 매도는 존재
        assertNull(orderById().get("B-1"));
        assertSame(cheap, orderById().get("B-2"));
        assertSame(order, orderById().get("S-1"));
        assertEquals(2, orderById().size());
    }
}