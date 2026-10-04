import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class MockedTransport implements Transport {
    private static final Logger logger = Logger.getLogger(MockedTransport.class.getName());

    private final Map<String, Follower> followers = new HashMap<>();
    private final Map<String, Long> delays = new HashMap<>();

    void registerFollower(String destination, Follower follower) {
        followers.put(destination, follower);
    }

    void setDelay(String destination, long delayMs) {
        delays.put(destination, delayMs);
    }

    void removeDelay(String destination) {
        delays.remove(destination);
    }

    @Override
    public int send(String destination, LogEntry entry) {
        Long delay = delays.get(destination);
        if (delay != null) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Follower follower = followers.get(destination);
        return follower.replicateMsg(entry);
    }
}