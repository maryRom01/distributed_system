import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Master extends Node {
    List<String> followerUrls;
    private final Transport transport;
    private final HeartbeatMonitor monitor;

    private final ExecutorService sendPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    Master(String id, Transport transport) {
        super(id);
        this.transport = transport;
        this.monitor = new HeartbeatMonitor(transport);
        followerUrls = new ArrayList<>();
    }

    void addFollower(String url) {
        followerUrls.add(url);
    }

    int allWriteConcern() {
        return followerUrls.size() + 1;
    }

    void appendMsg(String message, int writeConcern) {
        LogEntry logEntry = createEntry(message);
        int index = logEntry.getIndex();

        CountDownLatch latch = new CountDownLatch(writeConcern - 1);

        for (String followerUrl : followerUrls) {
            sendPool.submit(() -> {
                sendWithRetry(followerUrl, logEntry);
                latch.countDown();
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        logger.info("Write concern w=" + writeConcern + " satisfied for entry " + index);
    }

    private void sendWithRetry(String followerUrl, LogEntry entry) {
        long delayMs = 500;
        int attempt = 1;
        while (true) {
            try {
                monitor.awaitAvailable(followerUrl);
                transport.send(followerUrl, entry);
                return;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warning("Attempt " + attempt + " to " + followerUrl + " for entry "
                        + entry.getIndex() + " failed: " + e.getMessage()
                        + ". Retrying in " + delayMs + " ms");
                try { Thread.sleep(delayMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                delayMs = Math.min(delayMs * 2, 10000);
                attempt++;
            }
        }
    }

    private synchronized LogEntry createEntry(String message) {
        int index = log.size();
        LogEntry entry = new LogEntry(index, Instant.now().toString(), message, null);
        log.add(entry);
        return entry;
    }

    void startHeartbeats(long intervalMs) {
        for (String url : followerUrls) {
            monitor.watch(url, intervalMs);
        }
    }

    java.util.Map<String, String> followerStatuses() {
        return monitor.snapshot();
    }
}