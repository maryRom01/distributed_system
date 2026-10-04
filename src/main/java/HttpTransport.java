import com.google.gson.Gson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class HttpTransport implements Transport {
    private static final Logger logger = Logger.getLogger(HttpTransport.class.getName());
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @Override
    public int send(String destination, LogEntry entry) {
        try {
            String json = gson.toJson(entry);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(destination + "/replicate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Destination " + destination + " responded: " + response.body());
            return Integer.parseInt(response.body());

        } catch (IOException | InterruptedException e) {
            logger.severe("Failed to send to " + destination + ": " + e.getMessage());
            throw new RuntimeException("Failed to send to " + destination, e);
        }
    }
}
