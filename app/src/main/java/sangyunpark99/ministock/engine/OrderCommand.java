package sangyunpark99.ministock.engine;

import sangyunpark99.ministock.Order;
import sangyunpark99.ministock.Trade;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public record OrderCommand(
        CompletableFuture<List<Trade>> result,
        Order order
) implements Command{
}
