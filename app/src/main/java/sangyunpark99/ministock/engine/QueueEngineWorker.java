package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Trade;
import sangyunpark99.ministock.journal.Journal;

import java.util.List;
import java.util.concurrent.BlockingQueue;

public class QueueEngineWorker implements Runnable{

    private long sequence;
    private final BlockingQueue<Command> blockingQueue;
    private final MatchingEngine orderBook;
    private final Journal journal;

    public QueueEngineWorker(BlockingQueue<Command> blockingQueue, MatchingEngine orderBook, Journal journal) {
        this.blockingQueue = blockingQueue;
        this.orderBook = orderBook;
        this.journal = journal;
    }

    @Override
    public void run() {
        while(true){

            try {
                Command command = blockingQueue.take();
                sequence++;

                switch (command) {
                    case OrderCommand oc -> {
                        journal.append(OrderRecord.of(sequence, oc.order()));
                        List<Trade> result = orderBook.order(oc.order());
                        oc.result().complete(result);
                    }

                    case CancelCommand cc -> {
                        journal.append(new CancelRecord(sequence, cc.orderId()));
                        boolean result = orderBook.cancelOrder(cc.orderId());
                        cc.result().complete(result);
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
