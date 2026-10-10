package sangyunpark99.ministock;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;

public class OrderBook {

    private final TreeMap<Long, List<Order>> bids = new TreeMap<>();
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
    public void sellOrder() {
        // 매수 호가에서 가장 비싼 주문을 가져온다.
    }

    // 호가창 주문 취소
}