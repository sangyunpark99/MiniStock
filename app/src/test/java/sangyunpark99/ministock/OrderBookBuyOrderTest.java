package sangyunpark99.ministock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("호가창 매수 주문 (buyOrder)")
class OrderBookBuyOrderTest {

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
    private TreeMap<Long, List<Order>> book(String name) {
        try {
            Field f = OrderBook.class.getDeclaredField(name);
            f.setAccessible(true);
            return (TreeMap<Long, List<Order>>) f.get(orderBook);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void restAsk(Order order) {
        List<Order> level = book("asks").computeIfAbsent(order.getPrice(), k -> new ArrayList<>());
        level.add(order);
    }

    private List<Order> asksAt(long price) {
        return book("asks").getOrDefault(price, List.of());
    }

    private List<Order> bidsAt(long price) {
        return book("bids").getOrDefault(price, List.of());
    }

    @Test
    @DisplayName("매도 호가가 없으면 매수 주문은 매수 호가에 쌓인다")
    void noAsks() {
        Order order = buy("B-1", 10_000, 5);

        orderBook.buyOrder(order);

        assertEquals(List.of(order), bidsAt(10_000));
    }

    @Test
    @DisplayName("매도 호가가 없을 때 같은 가격 매수가 두 번 오면 순서대로 쌓인다")
    void samePriceTwice() {
        Order first = buy("B-1", 10_000, 3);
        Order second = buy("B-2", 10_000, 4);

        orderBook.buyOrder(first);
        orderBook.buyOrder(second);

        assertEquals(List.of(first, second), bidsAt(10_000));
    }

    @Test
    @DisplayName("최우선 매도 호가가 내 가격보다 비싸면 체결 없이 매수 호가에 쌓인다")
    void askTooExpensive() {
        Order ask = sell("S-1", 10_100, 5);
        restAsk(ask);
        Order order = buy("B-1", 10_000, 5);

        orderBook.buyOrder(order);

        assertEquals(5, ask.getCount());
        assertEquals(List.of(ask), asksAt(10_100));
        assertEquals(List.of(order), bidsAt(10_000));
    }

    @Test
    @DisplayName("가격과 수량이 같으면 전량 체결되고 양쪽 호가에서 사라진다")
    void fullMatch() {
        restAsk(sell("S-1", 10_050, 3));
        Order order = buy("B-1", 10_050, 3);

        orderBook.buyOrder(order);

        assertEquals(0, order.getCount());
        assertFalse(book("asks").containsKey(10_050L));
        assertFalse(book("bids").containsKey(10_050L));
    }

    @Test
    @DisplayName("같은 가격의 매도 주문들과 순서대로 체결되고, 상대의 남은 수량은 맨 앞에 남는다")
    void partialFillWithinPriceLevel() {
        Order b = sell("S-B", 10_050, 3);
        Order c = sell("S-C", 10_050, 4);
        restAsk(b);
        restAsk(c);
        Order order = buy("B-1", 10_100, 6);

        orderBook.buyOrder(order);

        assertEquals(0, order.getCount());       // 6주 모두 체결
        assertEquals(0, b.getCount());           // B 3주 체결
        assertEquals(1, c.getCount());           // C 3주 체결, 1주 남음
        assertEquals(List.of(c), asksAt(10_050));
        assertFalse(book("bids").containsKey(10_100L));
    }

    @Test
    @DisplayName("한 가격 레벨을 다 소진하면 다음 가격 레벨로 넘어가 체결한다")
    void sweepMultiplePriceLevels() {
        restAsk(sell("S-1", 10_050, 3));
        Order next = sell("S-2", 10_100, 5);
        restAsk(next);
        Order order = buy("B-1", 10_100, 6);

        orderBook.buyOrder(order);

        assertEquals(0, order.getCount());
        assertFalse(book("asks").containsKey(10_050L));   // 빈 가격 레벨 제거
        assertEquals(2, next.getCount());
        assertEquals(List.of(next), asksAt(10_100));
    }

    @Test
    @DisplayName("내 가격을 넘는 호가에서 멈추고, 남은 수량은 매수 호가에 쌓인다")
    void stopAtLimitPriceAndRestRemainder() {
        restAsk(sell("S-1", 10_050, 3));
        Order expensive = sell("S-2", 10_200, 5);
        restAsk(expensive);
        Order order = buy("B-1", 10_100, 6);

        orderBook.buyOrder(order);

        assertEquals(3, order.getCount());               // 3주 체결, 3주 남음
        assertFalse(book("asks").containsKey(10_050L));
        assertEquals(5, expensive.getCount());           // 비싼 호가는 그대로
        assertEquals(List.of(order), bidsAt(10_100));
    }
}