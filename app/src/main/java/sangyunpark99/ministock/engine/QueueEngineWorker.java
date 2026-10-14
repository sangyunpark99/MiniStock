package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.Trade;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public class QueueEngineWorker implements Runnable{

    private final BlockingQueue<OrderCommand> blockingQueue;
    private final MatchingEngine orderBook;

    public QueueEngineWorker(BlockingQueue<OrderCommand> blockingQueue, MatchingEngine orderBook) {
        this.blockingQueue = blockingQueue;
        this.orderBook = orderBook;
    }

    @Override
    public void run() {
        while(true){
            try {
                OrderCommand orderCommand = blockingQueue.take();
                Order order = orderCommand.order(); // 주문 꺼내기

                List<Trade> result = orderBook.order(order); // 주문 체결하기
                orderCommand.result().complete(result); // 트레이드 목록

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 지워지는 중단 표시 원상 복구
                break;
            }
        }
    }
}
