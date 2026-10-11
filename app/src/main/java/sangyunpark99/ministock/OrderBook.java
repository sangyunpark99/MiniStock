package sangyunpark99.ministock;

import javax.swing.*;
import java.util.*;

public class OrderBook {

    private final TreeMap<Long, List<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, List<Order>> asks = new TreeMap<>();
    private final HashMap<String, Order> orderById = new HashMap<>();

    // 호가창 매수 주문
    public void buyOrder(Order buy) {
        // 맨 앞 주문을 본다.
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
        }
    }

    // 호가창 매도 주문
    public void sellOrder(Order sell) {

        while(sell.getCount() > 0 && !bids.isEmpty()) {

            List<Order> bidsOrder = bids.firstEntry().getValue();
            long price = bids.firstKey();

            // 매도 호가

            if(price >= sell.getPrice()) { // 가격이 맞다면 구매
                while(sell.getCount() > 0 && !bidsOrder.isEmpty()) {
                    Order order = bidsOrder.getFirst();

                    if(sell.getCount() >= order.getCount()) {
                        sell.decreaseCount(order.getCount());
                        order.decreaseCount(order.getCount());
                        bidsOrder.removeFirst();
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
        }
    }
}