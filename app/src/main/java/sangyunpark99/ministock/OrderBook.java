package sangyunpark99.ministock;

import javax.swing.*;
import java.util.*;

public class OrderBook {

    private final TreeMap<Long, List<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, List<Order>> asks = new TreeMap<>();
    private final HashMap<String, Order> orderById = new HashMap<>();

    // 호가창 매수 주문(지정가)
    public void buyOrder(Order buy) {
        while(buy.getCount() > 0 && !asks.isEmpty()) {
            List<Order> asksOrder = asks.firstEntry().getValue();
            long price = asks.firstKey();

            if(price <= buy.getPrice()) {
                while(buy.getCount() > 0 && !asksOrder.isEmpty()) {
                    Order order = asksOrder.getFirst();

                    if(buy.getCount() >= order.getCount()) {
                        buy.decreaseCount(order.getCount());
                        order.decreaseCount(order.getCount());
                        asksOrder.removeFirst();
                        orderById.remove(order.getOrderId());
                    } else {
                        order.decreaseCount(buy.getCount());
                        buy.decreaseCount(buy.getCount());
                    }
                }

                if(asksOrder.isEmpty()) {
                    asks.remove(price);
                }

                continue;
            }

            break;
        }

        if(buy.getCount() != 0) {
            if(bids.containsKey(buy.getPrice())) {
                bids.get(buy.getPrice()).add(buy);
            } else {
                bids.put(buy.getPrice(), new ArrayList<>(List.of(buy)));
            }
            orderById.put(buy.getOrderId(), buy);
        }
    }

    // 호가창 매도 주문(지정가)
    public void sellOrder(Order sell) {

        while(sell.getCount() > 0 && !bids.isEmpty()) {
            List<Order> bidsOrder = bids.firstEntry().getValue();
            long price = bids.firstKey();

            if(price >= sell.getPrice()) {
                while(sell.getCount() > 0 && !bidsOrder.isEmpty()) {
                    Order order = bidsOrder.getFirst();

                    if(sell.getCount() >= order.getCount()) {
                        sell.decreaseCount(order.getCount());
                        order.decreaseCount(order.getCount());
                        bidsOrder.removeFirst();
                        orderById.remove(order.getOrderId());
                    } else {
                        order.decreaseCount(sell.getCount());
                        sell.decreaseCount(sell.getCount());
                    }
                }

                if(bidsOrder.isEmpty()) {
                    bids.remove(price);
                }

                continue;
            }

            break;
        }

        if(sell.getCount() != 0) {
            if(asks.containsKey(sell.getPrice())) {
                asks.get(sell.getPrice()).add(sell);
            } else {
                asks.put(sell.getPrice(), new ArrayList<>(List.of(sell)));
            }
            orderById.put(sell.getOrderId(), sell);
        }
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