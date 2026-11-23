public interface Transport {
    int send(String destination, LogEntry entry);
    void ping(String destination);
}
