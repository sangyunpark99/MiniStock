package sangyunpark99.ministock.journal;

import sangyunpark99.ministock.engine.CommandRecord;

public interface Journal {

    void append(CommandRecord command);
}
