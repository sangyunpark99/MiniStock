package sangyunpark99.ministock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sangyunpark99.ministock.engine.OrderBook;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("호가창 주문 취소 (cancelOrder)")
class OrderBookCancelOrderTest {

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

    private List<Order> asksAt(long price) {
        return book("asks").getOrDefault(price, List.of());
    }

    private List<Order> bidsAt(long price) {
        return book("bids").getOrDefault(price, List.of());
    }

    @Test
    @DisplayName("호가창에 쌓인 매수 주문을 취소하면 매수 호가와 orderById에서 사라진다")
    void cancelRestingBuy() {
        orderBook.order(buy("B-1", 10_000, 5));

        boolean result = orderBook.cancelOrder("B-1");

        assertTrue(result);
        assertFalse(book("bids").containsKey(10_000L));   // 빈 가격 레벨 제거
        assertNull(orderById().get("B-1"));
        assertTrue(orderById().isEmpty());
    }

    @Test
    @DisplayName("호가창에 쌓인 매도 주문을 취소하면 매도 호가와 orderById에서 사라진다")
    void cancelRestingSell() {
        orderBook.order(sell("S-1", 10_000, 5));

        boolean result = orderBook.cancelOrder("S-1");

        assertTrue(result);
        assertFalse(book("asks").containsKey(10_000L));
        assertNull(orderById().get("S-1"));
        assertTrue(orderById().isEmpty());
    }

    @Test
    @DisplayName("같은 가격 레벨의 중간 주문을 취소하면 나머지 주문의 순서는 유지된다")
    void cancelMiddleOfPriceLevel() {
        Order first = buy("B-1", 10_000, 1);
        Order middle = buy("B-2", 10_000, 2);
        Order last = buy("B-3", 10_000, 3);
        orderBook.order(first);
        orderBook.order(middle);
        orderBook.order(last);

        boolean result = orderBook.cancelOrder("B-2");

        assertTrue(result);
        assertEquals(List.of(first, last), bidsAt(10_000));
        assertNull(orderById().get("B-2"));
        assertEquals(2, orderById().size());
    }

    @Test
    @DisplayName("같은 가격 레벨의 맨 앞 주문을 취소해도 가격 레벨은 남아 있다")
    void cancelFirstOfPriceLevel() {
        Order first = sell("S-1", 10_000, 1);
        Order second = sell("S-2", 10_000, 2);
        orderBook.order(first);
        orderBook.order(second);

        orderBook.cancelOrder("S-1");

        assertEquals(List.of(second), asksAt(10_000));
        assertSame(second, orderById().get("S-2"));
    }

    @Test
    @DisplayName("다른 가격 레벨의 주문은 영향을 받지 않는다")
    void otherPriceLevelsUntouched() {
        Order a = buy("B-1", 10_000, 1);
        Order b = buy("B-2", 9_900, 2);
        orderBook.order(a);
        orderBook.order(b);

        orderBook.cancelOrder("B-1");

        assertFalse(book("bids").containsKey(10_000L));
        assertEquals(List.of(b), bidsAt(9_900));
        assertSame(b, orderById().get("B-2"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 ID를 취소하면 false를 반환하고 호가창은 그대로다")
    void cancelUnknownOrder() {
        Order order = buy("B-1", 10_000, 5);
        orderBook.order(order);

        boolean result = orderBook.cancelOrder("NOT-EXIST");

        assertFalse(result);
        assertEquals(List.of(order), bidsAt(10_000));
        assertEquals(1, orderById().size());
    }

    @Test
    @DisplayName("이미 전량 체결된 주문을 취소하면 false를 반환한다")
    void cancelFilledOrder() {
        orderBook.order(sell("S-1", 10_000, 5));
        orderBook.order(buy("B-1", 10_000, 5));   // S-1, B-1 모두 전량 체결

        assertFalse(orderBook.cancelOrder("S-1"));
        assertFalse(orderBook.cancelOrder("B-1"));
    }

    @Test
    @DisplayName("부분 체결된 주문을 취소하면 남은 수량이 호가창에서 사라진다")
    void cancelPartiallyFilledOrder() {
        Order ask = sell("S-1", 10_000, 5);
        orderBook.order(ask);
        orderBook.order(buy("B-1", 10_000, 3));   // S-1은 2주 남음
        assertEquals(2, ask.getCount());

        boolean result = orderBook.cancelOrder("S-1");

        assertTrue(result);
        assertFalse(book("asks").containsKey(10_000L));
        assertTrue(orderById().isEmpty());
    }

    @Test
    @DisplayName("같은 주문을 두 번 취소하면 두 번째는 false를 반환한다")
    void cancelTwice() {
        orderBook.order(buy("B-1", 10_000, 5));

        assertTrue(orderBook.cancelOrder("B-1"));
        assertFalse(orderBook.cancelOrder("B-1"));
    }

    @Test
    @DisplayName("취소된 주문은 이후 들어온 주문과 체결되지 않는다")
    void canceledOrderIsNotMatched() {
        Order canceled = sell("S-1", 10_000, 5);
        Order alive = sell("S-2", 10_100, 5);
        orderBook.order(canceled);
        orderBook.order(alive);
        orderBook.cancelOrder("S-1");

        Order order = buy("B-1", 10_100, 5);
        orderBook.order(order);

        assertEquals(0, alive.getCount());             // 살아 있는 S-2와 체결
        assertEquals(0, order.getCount());
        assertTrue(book("asks").isEmpty());
        assertTrue(orderById().isEmpty());
    }
}
