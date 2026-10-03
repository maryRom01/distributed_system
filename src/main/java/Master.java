import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class Master extends Node {
    List<String> followerUrls;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    Master(String id) {
        super(id);
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

        for (String followerUrl : followerUrls) {
            replicateToFollower(followerUrl, logEntry);
        }
    }

    private int replicateToFollower(String followerUrl, LogEntry entry) {
        try {
            String json = gson.toJson(entry);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(followerUrl + "/replicate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("Follower " + followerUrl + " responded: " + response.body());
            return Integer.parseInt(response.body());

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to replicate to " + followerUrl, e);
        }
    }
}