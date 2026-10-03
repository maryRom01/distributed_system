import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class FollowerApp2 {
    public static void main(String[] args) throws IOException {
        //Follower folLower = new Follower("follower-1");
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8002;
        Follower folLower = new Follower("follower-" + port);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        Gson gson = new Gson();

        server.createContext("/list", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                try {
                    List<LogEntry> log = folLower.getLog();
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

        server.createContext("/replicate", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                LogEntry entry = gson.fromJson(json, LogEntry.class);
                int index = folLower.replicateMsg(entry);
                String response = String.valueOf(index);
                byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(responseBytes);
                os.close();
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Follower started on port " + port);
    }
}
