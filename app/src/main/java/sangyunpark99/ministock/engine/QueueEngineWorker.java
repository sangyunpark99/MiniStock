package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Trade;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public class QueueEngineWorker implements Runnable{

    private final BlockingQueue<Command> blockingQueue;
    private final MatchingEngine orderBook;

    public QueueEngineWorker(BlockingQueue<Command> blockingQueue, MatchingEngine orderBook) {
        this.blockingQueue = blockingQueue;
        this.orderBook = orderBook;
    }

    @Override
    public void run() {
        while(true){

            try {
                Command command = blockingQueue.take();

                switch (command) {
                    case OrderCommand oc -> {
                        List<Trade> result = orderBook.order(oc.order());
                        oc.result().complete(result);
                    }

                    case CancelCommand cc -> {
                        boolean result = orderBook.cancelOrder(cc.orderId());
                        cc.result().complete(result);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 지워지는 중단 표시 원상 복구
                break;
            }
        }
    }
}
