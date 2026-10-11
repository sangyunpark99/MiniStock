package sangyunpark99.ministock;

import java.time.LocalDateTime;

public class Trade {

    private final String bidOrderId; // 매수

    private final String askOrderId; // 매도

    private final long price;

    private final int count;

    private final LocalDateTime createdAt;

    public Trade(String bidOrderId, String askOrderId, long price, int count) {
        this.bidOrderId = bidOrderId;
        this.askOrderId = askOrderId;
        this.price = price;
        this.count = count;
        this.createdAt = LocalDateTime.now();
    }

    public String getBidOrderId() {
        return this.bidOrderId;
    }

    public String getAskOrderId() {
        return this.askOrderId;
    }

    public long getPrice() {
        return this.price;
    }

    public int getCount() {
        return this.count;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
}
