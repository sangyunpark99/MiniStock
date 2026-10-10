package sangyunpark99.ministock;

import java.time.LocalDateTime;

public class Order {

    private final String symbol;

    private final OrderType orderType;

    private final Long price;

    private int count;

    private final String orderId;

    private final LocalDateTime createdAt;

    public Order(String symbol, OrderType orderType, Long price, int count, String orderId) {
        this.symbol = symbol;
        this.orderType = orderType;
        this.price = price;
        this.count = count;
        this.orderId = orderId;
        this.createdAt = LocalDateTime.now();
    }

    public String getSymbol() {
        return this.symbol;
    }

    public OrderType getOrderType() {
        return this.orderType;
    }

    public Long getPrice() {
        return this.price;
    }

    public int getCount() {
        return this.count;
    }

    public String getOrderId() {
        return this.orderId;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void decreaseOneCount() {
        this.count -= 1;
    }

    public void decreaseCount(int minusCount) {
        this.count -= minusCount;
    }
}