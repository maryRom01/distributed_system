import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class Master extends Node {
    List<String> followerUrls;
    private final Transport transport;

    Master(String id, Transport transport) {
        super(id);
        this.transport = transport;
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
            CompletableFuture.runAsync(() -> {
                transport.send(followerUrl, logEntry);
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

    private synchronized LogEntry createEntry(String message) {
        int index = log.size();
        LogEntry entry = new LogEntry(index, Instant.now().toString(), message, null);
        log.add(entry);
        return entry;
    }
}