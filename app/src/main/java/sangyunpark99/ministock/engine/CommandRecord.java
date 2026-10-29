package sangyunpark99.ministock.engine;

public sealed interface CommandRecord permits OrderRecord, CancelRecord{
    long sequence();
}
