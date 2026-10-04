public interface Transport {
    int send(String destination, LogEntry entry);
}
