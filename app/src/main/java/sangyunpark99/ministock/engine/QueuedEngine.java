package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.Trade;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;

public class QueuedEngine implements MatchingEngine {

    private final BlockingQueue<OrderCommand> queue;
    private final OrderBook orderBook;
    private final Thread workerThread;
    private final QueueEngineWorker queueEngineWorker;

    public QueuedEngine(OrderBook orderBook) {
        this.queue = new ArrayBlockingQueue<>(1000);
        this.orderBook = orderBook;
        this.queueEngineWorker = new QueueEngineWorker(queue, orderBook);
        this.workerThread = new Thread(this.queueEngineWorker, "matching-engine-worker");
        workerThread.start();
    }

    @Override
    public List<Trade> order(Order order) {

        OrderCommand command = new OrderCommand(new CompletableFuture<List<Trade>>(), order);

        try {
            queue.put(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("주문을 큐에 넣지 못했습니다.", e);
        }

        return command.result().join();
    }

    @Override
    public boolean cancelOrder(String orderId) {
        return false;
    }
}
