package sangyunpark99.ministock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("체결 결과 (Trade) 반환")
class OrderBookTradeTest {

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

    private void assertTrade(Trade trade, String bidOrderId, String askOrderId, long price, int count) {
        assertAll(
                () -> assertEquals(bidOrderId, trade.getBidOrderId(), "매수 주문 ID"),
                () -> assertEquals(askOrderId, trade.getAskOrderId(), "매도 주문 ID"),
                () -> assertEquals(price, trade.getPrice(), "체결 가격"),
                () -> assertEquals(count, trade.getCount(), "체결 수량"),
                () -> assertNotNull(trade.getCreatedAt(), "체결 시각")
        );
    }

    @Nested
    @DisplayName("매수 주문이 들어올 때")
    class WhenBuy {

        @Test
        @DisplayName("체결 상대가 없으면 빈 목록을 반환한다 (null 아님)")
        void noAsks() {
            List<Trade> trades = orderBook.order(buy("B-1", 10_000, 5));

            assertNotNull(trades);
            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("가격이 맞지 않아 체결되지 않으면 빈 목록을 반환한다")
        void priceNotMatched() {
            orderBook.order(sell("S-1", 10_100, 5));

            List<Trade> trades = orderBook.order(buy("B-1", 10_000, 5));

            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("한 주문과 전량 체결되면 Trade 1건을 반환한다")
        void singleFullMatch() {
            orderBook.order(sell("S-1", 10_050, 3));

            List<Trade> trades = orderBook.order(buy("B-1", 10_050, 3));

            assertEquals(1, trades.size());
            assertTrade(trades.get(0), "B-1", "S-1", 10_050, 3);
        }

        @Test
        @DisplayName("체결 가격은 내 가격이 아니라 기다리던 매도 주문의 가격이다")
        void tradePriceIsMakerPrice() {
            orderBook.order(sell("S-1", 10_050, 3));

            List<Trade> trades = orderBook.order(buy("B-1", 10_100, 3));

            assertEquals(1, trades.size());
            assertEquals(10_050, trades.get(0).getPrice());   // 10,100이 아님
        }

        @Test
        @DisplayName("같은 가격의 여러 주문과 체결되면 체결 순서대로 Trade가 여러 건 생긴다")
        void multipleTradesWithinPriceLevel() {
            orderBook.order(sell("S-B", 10_050, 3));
            orderBook.order(sell("S-C", 10_050, 4));

            List<Trade> trades = orderBook.order(buy("B-1", 10_100, 6));

            assertEquals(2, trades.size());
            assertTrade(trades.get(0), "B-1", "S-B", 10_050, 3);
            assertTrade(trades.get(1), "B-1", "S-C", 10_050, 3);
        }

        @Test
        @DisplayName("여러 가격 레벨에 걸쳐 체결되면 각 Trade는 해당 레벨의 가격을 가진다")
        void tradesAcrossPriceLevels() {
            orderBook.order(sell("S-1", 10_050, 3));
            orderBook.order(sell("S-2", 10_100, 5));

            List<Trade> trades = orderBook.order(buy("B-1", 10_100, 6));

            assertEquals(2, trades.size());
            assertTrade(trades.get(0), "B-1", "S-1", 10_050, 3);
            assertTrade(trades.get(1), "B-1", "S-2", 10_100, 3);
        }

        @Test
        @DisplayName("일부만 체결되면 체결된 만큼만 Trade로 반환하고, Trade 수량의 합은 체결 수량과 같다")
        void partialFillReturnsOnlyFilledTrades() {
            orderBook.order(sell("S-1", 10_050, 3));
            orderBook.order(sell("S-2", 10_200, 5));   // 내 가격보다 비쌈
            Order order = buy("B-1", 10_100, 6);

            List<Trade> trades = orderBook.order(order);

            assertEquals(1, trades.size());
            assertTrade(trades.get(0), "B-1", "S-1", 10_050, 3);

            int filled = trades.stream().mapToInt(Trade::getCount).sum();
            assertEquals(6 - order.getCount(), filled);    // 체결 수량 = 원래 수량 - 남은 수량
        }
    }

    @Nested
    @DisplayName("매도 주문이 들어올 때")
    class WhenSell {

        @Test
        @DisplayName("체결 상대가 없으면 빈 목록을 반환한다 (null 아님)")
        void noBids() {
            List<Trade> trades = orderBook.order(sell("S-1", 10_000, 5));

            assertNotNull(trades);
            assertTrue(trades.isEmpty());
        }

        @Test
        @DisplayName("체결 가격은 기다리던 매수 주문의 가격이고, bid/ask ID가 올바르게 들어간다")
        void tradePriceIsMakerPrice() {
            orderBook.order(buy("B-1", 10_050, 3));

            List<Trade> trades = orderBook.order(sell("S-1", 10_000, 3));

            assertEquals(1, trades.size());
            // 매도가 들어왔어도 bid = 매수 주문, ask = 매도 주문
            assertTrade(trades.get(0), "B-1", "S-1", 10_050, 3);
        }

        @Test
        @DisplayName("가장 비싼 매수 호가부터 체결되어 Trade가 그 순서대로 생긴다")
        void highestBidFirst() {
            orderBook.order(buy("B-2", 10_050, 3));
            orderBook.order(buy("B-1", 10_100, 2));

            List<Trade> trades = orderBook.order(sell("S-1", 10_000, 4));

            assertEquals(2, trades.size());
            assertTrade(trades.get(0), "B-1", "S-1", 10_100, 2);
            assertTrade(trades.get(1), "B-2", "S-1", 10_050, 2);
        }
    }

    @Test
    @DisplayName("호가창에 주문을 쌓기만 한 호출은 빈 목록을 반환하고, 체결이 일어난 호출만 Trade를 반환한다")
    void onlyMatchingCallReturnsTrades() {
        List<Trade> first = orderBook.order(buy("B-1", 9_900, 5));     // 쌓이기만 함
        List<Trade> second = orderBook.order(sell("S-1", 9_800, 5));  // 10분 뒤 체결

        assertTrue(first.isEmpty());
        assertEquals(1, second.size());
        assertTrade(second.get(0), "B-1", "S-1", 9_900, 5);
    }
}