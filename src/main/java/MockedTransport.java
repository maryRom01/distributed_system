import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class MockedTransport implements Transport {
    private static final Logger logger = Logger.getLogger(MockedTransport.class.getName());

    private final Map<String, Follower> followers = new HashMap<>();
    private final Map<String, Long> delays = new HashMap<>();
    private final Map<String, Integer> reorderCounts = new HashMap<>();
    private final Map<String, List<LogEntry>> storedMessages = new HashMap<>();

    // failures
    private final Set<String> unavailable = ConcurrentHashMap.newKeySet();
    private final Map<String, Integer> failAfterApplyLeft = new HashMap<>();

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

    // false = available; true = unavailable
    void setUnavailable(String destination, boolean down) {
        if (down) {
            unavailable.add(destination);
        } else {
            unavailable.remove(destination);
        }
    }

    // first <count> messages will be applied to follower, but then 'transport' throws 500
    synchronized void failAfterApply(String destination, int count) {
        failAfterApplyLeft.put(destination, count);
    }

    @Override
    public int send(String destination, LogEntry entry) {
        if (unavailable.contains(destination)) {
            throw new RuntimeException("Destination " + destination + " is unavailable");
        }

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

        if (shouldFailAfterApply(destination)) {
            throw new RuntimeException("Simulated 500 from " + destination + " after the entry was applied");
        }
        return entry.getIndex();
    }

    private synchronized boolean shouldFailAfterApply(String destination) {
        Integer left = failAfterApplyLeft.get(destination);
        if (left == null || left <= 0) {
            return false;
        }
        failAfterApplyLeft.put(destination, left - 1);
        return true;
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