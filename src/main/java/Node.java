import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

abstract class Node {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    List<LogEntry> log;
    String id;

    Node(String id) {
        this.id = id;
        this.log = new CopyOnWriteArrayList<>();
    }

    List<LogEntry> getLog() {
        return new CopyOnWriteArrayList<>(log);
    }
}

