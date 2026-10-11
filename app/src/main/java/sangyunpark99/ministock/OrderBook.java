package sangyunpark99.ministock;

import java.util.*;

public class OrderBook {

    private final TreeMap<Long, List<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, List<Order>> asks = new TreeMap<>();
    private final HashMap<String, Order> orderById = new HashMap<>();

    public List<Trade> order(Order reqOrder) {

        List<Trade> trades = new ArrayList<>();
        TreeMap<Long, List<Order>> oppositeOrderBook = reqOrder.getOrderType().equals(OrderType.BUY) ? asks : bids;
        TreeMap<Long, List<Order>> ownOrderBook = reqOrder.getOrderType().equals(OrderType.BUY) ? bids : asks;

        while (reqOrder.getCount() > 0 && !oppositeOrderBook.isEmpty()) {
            List<Order> orderBookOrder = oppositeOrderBook.firstEntry().getValue();
            long price = oppositeOrderBook.firstKey();

            if (checkMatch(reqOrder, price)) {
                while (reqOrder.getCount() > 0 && !orderBookOrder.isEmpty()) {
                    Order order = orderBookOrder.getFirst();

                    if (reqOrder.getCount() >= order.getCount()) {

                        int count = order.getCount();
                        reqOrder.decreaseCount(order.getCount());
                        order.decreaseCount(order.getCount());

                        trades.add(Trade.create(reqOrder.getOrderType(), reqOrder.getOrderId(), order.getOrderId(), price, count) );

                        orderBookOrder.removeFirst();
                        orderById.remove(order.getOrderId());
                    } else {

                        int count = reqOrder.getCount();
                        order.decreaseCount(reqOrder.getCount());
                        reqOrder.decreaseCount(reqOrder.getCount());

                        trades.add(Trade.create(reqOrder.getOrderType(), reqOrder.getOrderId(), order.getOrderId(), price, count));
                    }
                }

                if (orderBookOrder.isEmpty()) {
                    oppositeOrderBook.remove(price);
                }

                continue;
            }

            break;
        }

        if (reqOrder.getCount() != 0) {
            if (ownOrderBook.containsKey(reqOrder.getPrice())) {
                ownOrderBook.get(reqOrder.getPrice()).add(reqOrder);
            } else {
                ownOrderBook.put(reqOrder.getPrice(), new ArrayList<>(List.of(reqOrder)));
            }
            orderById.put(reqOrder.getOrderId(), reqOrder);
        }

        return trades;
    }

    private boolean checkMatch(Order reqOrder, long orderBookOrderPrice) {
        if(reqOrder.getOrderType().equals(OrderType.BUY)) { // 매수
            return orderBookOrderPrice <= reqOrder.getPrice();
        }

        return orderBookOrderPrice >= reqOrder.getPrice();
    }

    // 호가 주문 취소
    public boolean cancelOrder(String orderId) {
        Order order = orderById.get(orderId);
        if(order == null) return false;

        OrderType type = order.getOrderType();

        TreeMap<Long, List<Order>> orderbook;

        if(type.equals(OrderType.BUY)) {
            orderbook = bids;
        } else {
            orderbook = asks;
        }

        List<Order> orders = orderbook.get(order.getPrice());

        orders.remove(order);

        if(orders.isEmpty()) {
            orderbook.remove(order.getPrice());
        }

        orderById.remove(orderId);

        return true;
    }
}