package sangyunpark99.ministock.engine;

public record CancelRecord(
        long sequence,
        String orderId
) implements CommandRecord{
}
