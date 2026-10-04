public class Follower extends Node {
    Follower(String id) {
        super(id);
    }

    int replicateMsg(LogEntry entry) {
        log.add(entry);
        logger.info("Appended entry " + entry);
        return entry.getIndex();
    }
}

