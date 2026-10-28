package sangyunpark99.ministock.engine;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.OrderType;
import sangyunpark99.ministock.Trade;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("동시성: 여러 스레드에서 동시에 주문이 들어올 때")
class MatchingEngineConcurrencyTest {

    private static final String SYMBOL = "000660";
    private static final int ORDER_COUNT = 1_000;
    private static final int THREADS = 8;
    private static final int REPEAT = 20;
    private static final double CANCEL_RATIO = 0.3;

    static Stream<Arguments> engines() {
        Map<String, Function<OrderBook, MatchingEngine>> factories = new LinkedHashMap<>();
        factories.put("일반 락", ob -> new LockedEngine(ob, false));
        factories.put("공정 락", ob -> new LockedEngine(ob, true));
        factories.put("큐", ob -> new QueuedEngine(ob));

        List<Arguments> args = new ArrayList<>();
        factories.forEach((name, factory) -> {
            for (int seed = 1; seed <= REPEAT; seed++) {
                args.add(Arguments.of(name, factory, seed));
            }
        });
        return args.stream();
    }

    static IntStream seeds() {
        return IntStream.rangeClosed(1, REPEAT);
    }

    @ParameterizedTest(name = "[{0}] seed={2}")
    @MethodSource("engines")
    @DisplayName("스레드 8개로 동시에 넣어도 모든 기준을 만족한다")
    void concurrentOrdersKeepInvariants(String name,
                                        Function<OrderBook, MatchingEngine> factory,
                                        int seed) throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = factory.apply(orderBook);

        Result r = run(engine, THREADS, seed, 0.0);

        verifyInvariants(orderBook, r);
    }

    @ParameterizedTest(name = "[{0}] seed={2}")
    @MethodSource("engines")
    @DisplayName("스레드 8개로 주문과 취소를 섞어 넣어도 모든 기준을 만족한다")
    void concurrentOrdersAndCancelsKeepInvariants(String name,
                                                  Function<OrderBook, MatchingEngine> factory,
                                                  int seed) throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        MatchingEngine engine = factory.apply(orderBook);

        Result r = run(engine, THREADS, seed, CANCEL_RATIO);

        assertFalse(r.cancelled().isEmpty(), "취소가 한 건도 성공하지 않음 (테스트가 취소를 검증하지 못함)");
        verifyInvariants(orderBook, r);
    }

    @Test
    @DisplayName("[대조군] 동기화 없는 OrderBook도 스레드 1개면 모든 기준을 만족한다")
    void singleThreadRawOrderBook() throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        verifyInvariants(orderBook, run(orderBook, 1, 42, 0.0));
    }

    @Test
    @DisplayName("[대조군] 동기화 없는 OrderBook도 스레드 1개면 주문과 취소를 섞어도 모든 기준을 만족한다")
    void singleThreadRawOrderBookWithCancels() throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        Result r = run(orderBook, 1, 42, CANCEL_RATIO);

        assertFalse(r.cancelled().isEmpty(), "취소가 한 건도 성공하지 않음");
        verifyInvariants(orderBook, r);
    }

    @Test
    @Disabled("동기화 없는 OrderBook은 동시 접근 시 깨진다 (문제 재현용, 블로그 #2 참고)")
    @DisplayName("[재현] 동기화 없는 OrderBook에 스레드 8개로 넣으면 깨진다")
    void concurrentRawOrderBook() throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        verifyInvariants(orderBook, run(orderBook, THREADS, 42, 0.0));
    }

    @ParameterizedTest(name = "seed={0}")
    @MethodSource("seeds")
    @Disabled("취소가 큐를 우회하면 깨진다 (QueuedEngine 2단계 이전 코드 재현용)")
    @DisplayName("[재현] 주문은 큐로, 취소는 호가창 직접 호출하면 깨진다")
    void cancelBypassingQueueBreaks(int seed) throws InterruptedException {
        OrderBook orderBook = new OrderBook();
        QueuedEngine queued = new QueuedEngine(orderBook);

        MatchingEngine bypass = new MatchingEngine() {
            @Override
            public List<Trade> order(Order order) {
                return queued.order(order);
            }

            @Override
            public boolean cancelOrder(String orderId) {
                return orderBook.cancelOrder(orderId); // 2단계 이전: 큐를 거치지 않음
            }
        };

        verifyInvariants(orderBook, run(bypass, THREADS, seed, CANCEL_RATIO));
    }

    private record Result(List<Order> orders,
                          Map<String, Integer> initialCount,
                          List<Trade> trades,
                          Set<String> cancelled,
                          List<Throwable> errors,
                          boolean finished) {}

    private Result run(MatchingEngine engine, int threads, long seed, double cancelRatio)
            throws InterruptedException {
        Random random = new Random(seed);
        List<Order> orders = new ArrayList<>();
        Map<String, Integer> initialCount = new HashMap<>();

        for (int i = 0; i < ORDER_COUNT; i++) {
            OrderType type = (i % 2 == 0) ? OrderType.BUY : OrderType.SELL;
            long price = 9_950 + 10L * random.nextInt(11);
            int count = 1 + random.nextInt(10);
            String id = (type == OrderType.BUY ? "B-" : "S-") + i;

            orders.add(new Order(SYMBOL, type, price, count, id));
            initialCount.put(id, count);
        }

        Queue<Trade> trades = new ConcurrentLinkedQueue<>();
        Set<String> cancelled = ConcurrentHashMap.newKeySet();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);

        int chunk = (ORDER_COUNT + threads - 1) / threads;
        for (int t = 0; t < threads; t++) {
            List<Order> mine = orders.subList(Math.min(t * chunk, ORDER_COUNT),
                    Math.min((t + 1) * chunk, ORDER_COUNT));
            Random cancelRandom = new Random(seed * 31 + t); // 스레드마다 따로: Random 공유 금지

            pool.submit(() -> {
                try {
                    start.await();
                    List<String> sent = new ArrayList<>();
                    for (Order o : mine) {
                        trades.addAll(engine.order(o));
                        sent.add(o.getOrderId());

                        // 이미 보낸 주문 중 하나를 골라 취소: 호가창에 남아 있거나,
                        // 다른 스레드 주문과 체결되는 중일 수 있다
                        if (cancelRandom.nextDouble() < cancelRatio) {
                            String target = sent.get(cancelRandom.nextInt(sent.size()));
                            if (engine.cancelOrder(target)) {
                                cancelled.add(target);
                            }
                        }
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(10, TimeUnit.SECONDS);
        if (!finished) {
            Thread.getAllStackTraces().forEach((thread, stack) -> {
                if (thread.getName().startsWith("pool-")) {
                    System.out.println("=== " + thread.getName() + " (" + thread.getState() + ")");
                    for (int i = 0; i < Math.min(stack.length, 8); i++) {
                        System.out.println("    at " + stack[i]);
                    }
                }
            });
            pool.shutdownNow();
        }

        return new Result(orders, initialCount, new ArrayList<>(trades),
                Set.copyOf(cancelled), new ArrayList<>(errors), finished);
    }

    private void verifyInvariants(OrderBook orderBook, Result r) {
        assertTrue(r.finished(), "10초 안에 끝나지 않음 (무한 루프 의심)");
        assertTrue(r.errors().isEmpty(), () -> "예외 발생: " + r.errors().get(0));

        // 1. 수량 보존
        Map<String, Integer> filled = new HashMap<>();
        for (Trade t : r.trades()) {
            assertTrue(t.getCount() > 0, "체결 수량이 0 이하인 Trade");
            filled.merge(t.getBidOrderId(), t.getCount(), Integer::sum);
            filled.merge(t.getAskOrderId(), t.getCount(), Integer::sum);
        }
        for (Order o : r.orders()) {
            String id = o.getOrderId();
            int initial = r.initialCount().get(id);
            int f = filled.getOrDefault(id, 0);

            // 1-1. 모든 주문: 체결 합은 원래 수량을 넘을 수 없다
            assertTrue(f <= initial,
                    id + " 체결 합이 원래 수량 초과 (처음 " + initial + ", 체결 " + f + ")");

            if (r.cancelled().contains(id)) {
                // 1-2. 취소 성공 = 그 순간 남은 수량이 있었다 → 전부 체결됐을 수 없다
                assertTrue(f < initial,
                        id + " 취소에 성공했는데 전부 체결됨 (처음 " + initial + ", 체결 " + f + ")");
            } else {
                // 1-3. 취소 안 된 주문: 처음 = 체결 + 남음
                assertEquals(initial, f + o.getCount(),
                        id + " 수량 불일치 (처음 " + initial + ", 체결 " + f + ", 남음 " + o.getCount() + ")");
            }
        }

        // 2. 호가창 구조
        TreeMap<Long, List<Order>> bids = field(orderBook, "bids");
        TreeMap<Long, List<Order>> asks = field(orderBook, "asks");
        Map<String, Order> orderById = field(orderBook, "orderById");

        Set<Order> resting = Collections.newSetFromMap(new IdentityHashMap<>());
        collectResting(bids, OrderType.BUY, resting);
        collectResting(asks, OrderType.SELL, resting);

        // 3. 호가창과 orderById 일치
        assertEquals(resting.size(), orderById.size(), "호가창 주문 수와 orderById 크기 불일치");
        for (Order o : resting) {
            assertSame(o, orderById.get(o.getOrderId()), o.getOrderId() + " 이 orderById에 없음");
        }

        // 4. 남은 수량이 있는 주문만 호가창에 / 취소된 주문은 어디에도 없음
        for (Order o : r.orders()) {
            String id = o.getOrderId();
            if (r.cancelled().contains(id)) {
                assertFalse(resting.contains(o), id + " 취소됐는데 호가창에 남음");
                assertFalse(orderById.containsKey(id), id + " 취소됐는데 orderById에 남음");
            } else if (o.getCount() > 0) {
                assertTrue(resting.contains(o), id + " 수량이 남았는데 호가창에 없음");
            } else {
                assertFalse(resting.contains(o), id + " 다 체결됐는데 호가창에 남음");
            }
        }

        // 5. 호가 교차 없음
        if (!bids.isEmpty() && !asks.isEmpty()) {
            assertTrue(bids.firstKey() < asks.firstKey(),
                    "호가 교차: 최고 매수 " + bids.firstKey() + " >= 최저 매도 " + asks.firstKey());
        }
    }

    private void collectResting(TreeMap<Long, List<Order>> book, OrderType side, Set<Order> out) {
        for (Map.Entry<Long, List<Order>> level : book.entrySet()) {
            assertFalse(level.getValue().isEmpty(), "빈 가격 레벨이 남아 있음: " + level.getKey());
            for (Order o : level.getValue()) {
                assertEquals(side, o.getOrderType(), o.getOrderId() + " 이 반대편 호가창에 있음");
                assertEquals(level.getKey().longValue(), o.getPrice().longValue(), o.getOrderId() + " 가격 레벨 불일치");
                assertTrue(o.getCount() > 0, o.getOrderId() + " 수량 0인 주문이 호가창에 남음");
                assertTrue(out.add(o), o.getOrderId() + " 이 호가창에 두 번 들어 있음");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T field(OrderBook orderBook, String name) {
        try {
            Field f = OrderBook.class.getDeclaredField(name);
            f.setAccessible(true);
            return (T) f.get(orderBook);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}