package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.Trade;

import java.util.List;

public interface MatchingEngine {

    List<Trade> order(Order order);

    boolean cancelOrder(String orderId);
}
