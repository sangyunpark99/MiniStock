package sangyunpark99.ministock.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.OrderType;
import sangyunpark99.ministock.Trade;
import sangyunpark99.ministock.journal.InMemoryJournal;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("저널: 큐 엔진이 처리한 순서를 빠짐없이 기록하고, 재생하면 같은 결과가 나온다")
class QueuedEngineJournalTest {

    private static final String SYMBOL = "000660";
    private static final int ORDER_COUNT = 1_000;
    private static final int THREADS = 8;
    private static final int REPEAT = 20;
    private static final double CANCEL_RATIO = 0.3;

    static IntStream seeds() {
        return IntStream.rangeClosed(1, REPEAT);
    }

    @ParameterizedTest(name = "seed={0}")
    @MethodSource("seeds")
    @DisplayName("스레드 8개로 주문과 취소를 섞어 넣어도 저널이 정확하다")
    void journalRecordsAndReplays(int seed) throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        InMemoryJournal journal = new InMemoryJournal();
        QueuedEngine engine = new QueuedEngine(orderBook, journal);

        List<Order> orders = createOrders(seed);
        Queue<Trade> trades = new ConcurrentLinkedQueue<>();
        Queue<String> cancelRequests = new ConcurrentLinkedQueue<>();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        int chunk = (ORDER_COUNT + THREADS - 1) / THREADS;
        for (int t = 0; t < THREADS; t++) {
            List<Order> mine = orders.subList(Math.min(t * chunk, ORDER_COUNT),
                    Math.min((t + 1) * chunk, ORDER_COUNT));
            Random cancelRandom = new Random(seed * 31L + t);
            pool.submit(() -> {
                try {
                    start.await();
                    List<String> sent = new ArrayList<>();
                    for (Order o : mine) {
                        trades.addAll(engine.order(o));
                        sent.add(o.getOrderId());
                        if (cancelRandom.nextDouble() < CANCEL_RATIO) {
                            String target = sent.get(cancelRandom.nextInt(sent.size()));
                            cancelRequests.add(target);          // 성공 여부와 상관없이 보낸 취소는 모두 기록돼야 한다
                            engine.cancelOrder(target);
                        }
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "10초 안에 끝나지 않음");
        assertTrue(errors.isEmpty(), () -> "예외 발생: " + errors.peek());

        // 모든 요청이 끝난 뒤에 읽는다 (워커가 더 이상 기록하지 않는 시점)
        List<CommandRecord> records = new ArrayList<>(journal.getRecords());

        // 1. 순번이 1부터 빠짐없이 이어진다
        for (int i = 0; i < records.size(); i++) {
            assertEquals(i + 1, records.get(i).sequence(), (i + 1) + "번째 기록의 순번이 어긋남");
        }

        // 2. 저널 기록 수 = 보낸 명령 수 (주문 + 취소)
        assertEquals(orders.size() + cancelRequests.size(), records.size(), "기록 수와 보낸 명령 수가 다름");

        // 3. 보낸 주문은 OrderRecord로, 보낸 취소는 CancelRecord로 한 번씩 기록된다
        Map<String, Long> orderRecorded = records.stream()
                .filter(OrderRecord.class::isInstance)
                .map(r -> ((OrderRecord) r).orderId())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertEquals(orders.size(), orderRecorded.size(), "기록되지 않은 주문이 있음");
        orderRecorded.forEach((id, n) -> assertEquals(1L, n, id + " 주문이 " + n + "번 기록됨"));

        Map<String, Long> cancelRecorded = records.stream()
                .filter(CancelRecord.class::isInstance)
                .map(r -> ((CancelRecord) r).orderId())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        Map<String, Long> cancelSent = cancelRequests.stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertEquals(cancelSent, cancelRecorded, "보낸 취소와 기록된 취소가 다름");

        // 4. 저널을 순번 순서대로 새 OrderBook에 재생하면 같은 결과가 나온다
        List<String> originalTrades = tradeKeys(trades);
        List<String> originalBook = bookSnapshot(orderBook);   // 재생 전에 원래 호가창을 먼저 찍어 둔다

        OrderBook replayBook = new OrderBook();
        List<Trade> replayTrades = new ArrayList<>();
        for (CommandRecord r : records) {
            switch (r) {
                case OrderRecord o -> replayTrades.addAll(replayBook.order(o.toOrder()));
                case CancelRecord c -> replayBook.cancelOrder(c.orderId());
            }
        }

        assertEquals(originalTrades, tradeKeys(replayTrades), "재생한 체결 결과가 원래와 다름");
        assertEquals(originalBook, bookSnapshot(replayBook), "재생한 호가창이 원래와 다름");
    }

    private List<Order> createOrders(long seed) {
        Random random = new Random(seed);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < ORDER_COUNT; i++) {
            OrderType type = (i % 2 == 0) ? OrderType.BUY : OrderType.SELL;
            long price = 9_950 + 10L * random.nextInt(11);
            int count = 1 + random.nextInt(10);
            orders.add(new Order(SYMBOL, type, price, count, (type == OrderType.BUY ? "B-" : "S-") + i));
        }
        return orders;
    }

    /** 체결을 (매수, 매도, 가격, 수량)으로 바꿔 정렬한다. 원래 실행은 여러 스레드가 모아서 순서가 섞여 있으므로 집합으로 비교한다 */
    private List<String> tradeKeys(Collection<Trade> trades) {
        return trades.stream()
                .map(t -> t.getBidOrderId() + "|" + t.getAskOrderId() + "|" + t.getPrice() + "|" + t.getCount())
                .sorted()
                .toList();
    }

    /** 호가창을 가격 레벨 순서, 같은 가격 안에서는 대기 순서대로 (주문 ID:남은 수량)으로 펼친다 */
    private List<String> bookSnapshot(OrderBook book) {
        List<String> out = new ArrayList<>();
        for (String side : List.of("bids", "asks")) {
            TreeMap<Long, List<Order>> levels = field(book, side);
            levels.forEach((price, list) -> {
                for (Order o : list) {
                    out.add(side + "@" + price + ":" + o.getOrderId() + ":" + o.getCount());
                }
            });
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private <T> T field(OrderBook book, String name) {
        try {
            Field f = OrderBook.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(book);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
