package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.Trade;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class LockedEngine implements MatchingEngine{

    private final MatchingEngine matchingEngine;
    private final ReentrantLock lock;

    public LockedEngine(MatchingEngine matchingEngine, boolean fair) {
        this.matchingEngine = matchingEngine;
        this.lock = new ReentrantLock(fair);
    }

    @Override
    public List<Trade> order(Order order) {

        lock.lock();
        try {
            return matchingEngine.order(order);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean cancelOrder(String orderId) {

        lock.lock();
        try {
            return matchingEngine.cancelOrder(orderId);
        } finally {
            lock.unlock();
        }
    }
}
