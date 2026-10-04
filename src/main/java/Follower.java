public class Follower extends Node {
    private final Transport transport;

    Follower(String id, Transport transport) {
        super(id);
        this.transport = transport;
    }

    int replicateMsg(LogEntry entry) {
        log.add(entry);
        logger.info("Appended entry " + entry);
        return entry.getIndex();
    }
}

