import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class MockedTransport implements Transport {
    private static final Logger logger = Logger.getLogger(MockedTransport.class.getName());

    private final Map<String, Follower> followers = new HashMap<>();
    private final Map<String, Long> delays = new HashMap<>();
    private final Map<String, Integer> reorderCounts = new HashMap<>();
    private final Map<String, List<LogEntry>> storedMessages = new HashMap<>();

    void registerFollower(String destination, Follower follower) {
        followers.put(destination, follower);
    }

    void setDelay(String destination, long delayMs) {
        delays.put(destination, delayMs);
    }

    void removeDelay(String destination) {
        delays.remove(destination);
    }

    synchronized void reorderMessages(String destination, int count) {
        reorderCounts.put(destination, count);
        storedMessages.put(destination, new ArrayList<>());
    }

    synchronized void stopReordering(String destination) {
        reorderCounts.remove(destination);
        storedMessages.remove(destination);
    }

    @Override
    public int send(String destination, LogEntry entry) {
        List<LogEntry> toDeliver = collectForDelivery(destination, entry);

        // if < N messages, no delivery
        if (toDeliver.isEmpty()) {
            return entry.getIndex();
        }

        Long delay = delays.get(destination);
        if (delay != null) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        Follower follower = followers.get(destination);
        for (LogEntry e : toDeliver) {
            follower.replicateMsg(e);
        }
        return entry.getIndex();
    }

    private synchronized List<LogEntry> collectForDelivery(String destination, LogEntry entry) {
        List<LogEntry> result = new ArrayList<>();
        Integer count = reorderCounts.get(destination);

        if (count == null) {
            // no reordering
            result.add(entry);
            return result;
        }

        List<LogEntry> buffer = storedMessages.get(destination);
        buffer.add(entry);
        if (buffer.size() >= count) {
            for (int i = buffer.size() - 1; i >= 0; i--) {
                result.add(buffer.get(i));
            }
            buffer.clear();
        }
        return result;
    }
}