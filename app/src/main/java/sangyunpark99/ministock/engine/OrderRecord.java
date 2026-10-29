package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.OrderType;

public record OrderRecord(
        long sequence,
        String symbol,
        OrderType orderType,
        long price,
        int count,
        String orderId
) implements CommandRecord{

    public static OrderRecord of(long sequence, Order order) {
        return new OrderRecord(sequence, order.getSymbol(), order.getOrderType(),
                order.getPrice(), order.getCount(), order.getOrderId());
    }

    public Order toOrder() {
        return new Order(symbol, orderType, price, count, orderId);
    }
}
