import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    void appendMsg(String message) {
        int index = log.size();
        String timeStamp = Instant.now().toString();
        LogEntry logEntry = new LogEntry(index, timeStamp, message, null);
        log.add(logEntry);

        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (String followerUrl : followerUrls) {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> transport.send(followerUrl, logEntry));
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        logger.info("All followers acknowledged entry " + index);
    }
}