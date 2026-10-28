package sangyunpark99.ministock.engine;

import java.util.concurrent.CompletableFuture;

public record CancelCommand(
        CompletableFuture<Boolean> result,
        String orderId
) implements Command{
}
