import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class MasterApp {
    public static void main(String[] args) throws IOException {
        Master master = new Master("master-1", new HttpTransport());
//        // for Docker
//        master.addFollower("http://follower1:8001");
//        master.addFollower("http://follower2:8002");

        // for local run
        master.addFollower("http://localhost:8001");
        master.addFollower("http://localhost:8002");

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        Gson gson = new Gson();

        server.createContext("/list", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    List<LogEntry> log = master.getLog();
                    String json = gson.toJson(log);
                    byte[] responseBytes = json.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    String error = "Error: " + e.getMessage();
                    byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, errorBytes.length);
                    exchange.getResponseBody().write(errorBytes);
                    exchange.getResponseBody().close();
                }
            }
        });

        server.createContext("/append", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    int w = parseWriteConcern(exchange.getRequestURI().getQuery(), master.followerUrls.size() + 1);
                    if (w < 1 || w > master.followerUrls.size() + 1) {
                        String error = "Invalid w: must be between 1 and " + (master.followerUrls.size() + 1);
                        byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, errorBytes.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(errorBytes);
                        os.close();
                        return;
                    }
                    String message = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    master.appendMsg(message, w);
                    String response = "OK";
                    byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, responseBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(responseBytes);
                    os.close();
                } catch (NumberFormatException e) {
                    String error = "Invalid w: not a number";
                    byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(400, errorBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorBytes);
                    os.close();
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    String error = "Error: " + e.getMessage();
                    byte[] errorBytes = error.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(500, errorBytes.length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(errorBytes);
                    os.close();
                }
            }
        });

        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
        System.out.println("Master started on port 8000");
    }

    private static int parseWriteConcern(String query, int defaultWC) {
        if (query == null) return defaultWC;
        for (String param : query.split("&")) {
            String[] kv = param.split("=");
            if (kv.length == 2 && kv[0].equals("w")) {
                return Integer.parseInt(kv[1]);
            }
        }
        return defaultWC;
    }
}
