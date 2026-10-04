import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

abstract class Node {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    List<LogEntry> log;
    String id;

    Node(String id) {
        this.id = id;
        this.log = new ArrayList<>();
    }

    List<LogEntry> getLog() {
        return new ArrayList<>(log);
    }
}

