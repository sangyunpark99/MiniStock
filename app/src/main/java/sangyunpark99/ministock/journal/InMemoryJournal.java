package sangyunpark99.ministock.journal;

import sangyunpark99.ministock.engine.CommandRecord;

import java.util.ArrayList;
import java.util.List;

public class InMemoryJournal implements Journal{

    private final ArrayList<CommandRecord> records;

    public InMemoryJournal() {
        this.records = new ArrayList<>();
    }

    @Override
    public void append(CommandRecord command) {
        records.add(command);
    }

    public List<CommandRecord> getRecords() {
        return List.copyOf(records);
    }
}
