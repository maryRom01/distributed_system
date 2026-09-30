public class Follower extends Node {
    Follower(String id) {
        super(id);
    }

    int replicateMsg(LogEntry entry) {
        log.add(entry);
        return entry.getIndex();
    }
}

