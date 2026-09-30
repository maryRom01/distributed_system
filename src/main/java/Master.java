import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Master extends Node {
    List<Follower> followers;

    Master(String id) {
        super(id);
        followers = new ArrayList<>();
    }

    void addFollower(Follower follower) {
        followers.add(follower);
    }

    void appendMsg(String message) {
        int index = log.size();
        Instant timeStamp = Instant.now();
        LogEntry logEntry = new LogEntry(index, timeStamp, message, null);
        log.add(logEntry);
        for (Follower follower : followers) {
            int response = replicateToFollower(follower, logEntry);
            //System.out.println(response);
        }
    }

    private int replicateToFollower(Follower follower, LogEntry entry) {
        return follower.replicateMsg(entry);
    }

}

