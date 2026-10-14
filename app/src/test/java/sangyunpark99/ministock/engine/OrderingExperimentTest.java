package sangyunpark99.ministock.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.OrderType;
import sangyunpark99.ministock.Trade;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("[실험] 엔진별 처리 순서: 먼저 도착한 주문이 먼저 처리되는가")
class OrderingExperimentTest {

    private static final String SYMBOL = "000660";
    private static final int ORDER_COUNT = 10_000;
    private static final int THREADS = 8;
    private static final int RUNS = 10;

    /** 안쪽에 끼워 넣는 기록용 엔진: 실제로 처리된 순서를 번호로 남긴다 */
    static class RecordingEngine implements MatchingEngine {
        private final OrderBook delegate = new OrderBook();
        private final AtomicLong seq = new AtomicLong();
        final Map<String, Long> processedAt = new ConcurrentHashMap<>();

        @Override
        public List<Trade> order(Order order) {
            processedAt.put(order.getOrderId(), seq.getAndIncrement());
            return delegate.order(order);
        }

        @Override
        public boolean cancelOrder(String orderId) {
            return delegate.cancelOrder(orderId);
        }
    }

    record Stat(long inversions, long maxDisplacement) {}

    @Test
    void compareOrdering() throws InterruptedException {
        Map<String, Function<MatchingEngine, MatchingEngine>> engines = new LinkedHashMap<>();
        engines.put("일반 락", inner -> new LockedEngine(inner, false));
        engines.put("공정 락", inner -> new LockedEngine(inner, true));

        long totalPairs = (long) ORDER_COUNT * (ORDER_COUNT - 1) / 2;
        System.out.println("코어 수: " + Runtime.getRuntime().availableProcessors());
        System.out.printf("주문 %d개, 스레드 %d개, 조건별 %d회%n%n", ORDER_COUNT, THREADS, RUNS);

        List<String> summary = new ArrayList<>();

        for (var e : engines.entrySet()) {
            for (boolean yieldGap : new boolean[]{false, true}) {
                String label = e.getKey() + (yieldGap ? " + yield" : "");
                System.out.println("=== " + label);
                System.out.printf("%6s %12s %10s %10s%n", "seed", "역전 쌍", "역전율", "최대 밀림");

                List<Stat> stats = new ArrayList<>();
                for (int seed = 1; seed <= RUNS; seed++) {
                    Stat s = run(e.getValue(), seed, yieldGap);
                    stats.add(s);
                    System.out.printf("%6d %12d %9.4f%% %10d%n",
                            seed, s.inversions(), 100.0 * s.inversions() / totalPairs, s.maxDisplacement());
                }
                System.out.println();
                summary.add(summarize(label, stats));
            }
        }

        System.out.println("=== 요약 (역전 쌍: 최소 / 평균 / 최대, 최대 밀림: 최소 ~ 최대)");
        summary.forEach(System.out::println);
    }

    private String summarize(String label, List<Stat> stats) {
        LongSummaryStatistics inv = stats.stream().mapToLong(Stat::inversions).summaryStatistics();
        LongSummaryStatistics disp = stats.stream().mapToLong(Stat::maxDisplacement).summaryStatistics();
        return String.format("%-14s 역전 쌍 %8d / %10.1f / %8d   최대 밀림 %4d ~ %4d",
                label, inv.getMin(), inv.getAverage(), inv.getMax(), disp.getMin(), disp.getMax());
    }

    private Stat run(Function<MatchingEngine, MatchingEngine> factory, long seed, boolean yieldGap)
            throws InterruptedException {
        RecordingEngine recorder = new RecordingEngine();
        MatchingEngine engine = factory.apply(recorder);

        Random random = new Random(seed);
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < ORDER_COUNT; i++) {
            OrderType type = (i % 2 == 0) ? OrderType.BUY : OrderType.SELL;
            long price = 9_950 + 10L * random.nextInt(11);
            int count = 1 + random.nextInt(10);
            orders.add(new Order(SYMBOL, type, price, count, (type == OrderType.BUY ? "B-" : "S-") + i));
        }

        AtomicLong arrivalSeq = new AtomicLong();
        Map<String, Long> arrivedAt = new ConcurrentHashMap<>();
        Queue<Throwable> errors = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        int chunk = (ORDER_COUNT + THREADS - 1) / THREADS;
        for (int t = 0; t < THREADS; t++) {
            List<Order> mine = orders.subList(Math.min(t * chunk, ORDER_COUNT),
                    Math.min((t + 1) * chunk, ORDER_COUNT));
            pool.submit(() -> {
                try {
                    start.await();
                    for (Order o : mine) {
                        arrivedAt.put(o.getOrderId(), arrivalSeq.getAndIncrement()); // 도착 번호표
                        if (yieldGap) {
                            Thread.yield();                                           // 틈 넓히기
                        }
                        engine.order(o);                                              // 안에서 처리 번호표
                    }
                } catch (Throwable ex) {
                    errors.add(ex);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "시간 초과");
        assertTrue(errors.isEmpty(), () -> "예외: " + errors.peek());
        assertEquals(ORDER_COUNT, recorder.processedAt.size(), "처리 기록 누락");

        // 도착 순서로 정렬한 뒤, 처리 번호가 거꾸로 된 쌍을 센다
        long[] processed = orders.stream()
                .sorted(Comparator.comparingLong(o -> arrivedAt.get(o.getOrderId())))
                .mapToLong(o -> recorder.processedAt.get(o.getOrderId()))
                .toArray();

        long inversions = 0;
        long maxDisplacement = 0;
        for (int i = 0; i < processed.length; i++) {
            maxDisplacement = Math.max(maxDisplacement, Math.abs(processed[i] - i));
            for (int j = i + 1; j < processed.length; j++) {
                if (processed[i] > processed[j]) inversions++;
            }
        }
        return new Stat(inversions, maxDisplacement);
    }
}