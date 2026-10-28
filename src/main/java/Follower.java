import java.util.Map;
import java.util.TreeMap;

public class Follower extends Node {
    private final Transport transport;
    private final Map<Integer, LogEntry> buffer = new TreeMap<>();

    Follower(String id, Transport transport) {
        super(id);
        this.transport = transport;
    }

    synchronized int replicateMsg(LogEntry entry) {
        int index = entry.getIndex();

        if (index < log.size()) {
            logger.info("Duplicate entry ignored: " + index);
        } else if (index == log.size()) {
            log.add(entry);
            logger.info("Appended entry " + entry);
            LogEntry next;
            while ((next = buffer.remove(log.size())) != null) {
                log.add(next);
                logger.info("Appended entry " + entry);
            }
        } else {
            buffer.put(index, entry);
            logger.info("Entry " + index + " buffered, waiting for " + log.size());
        }

        return index;
    }
}

