package sangyunpark99.ministock.engine;

public sealed interface Command permits OrderCommand, CancelCommand {
}
