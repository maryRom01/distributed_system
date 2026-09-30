import java.time.Instant;

public class LogEntry {
    private final int index;
    private final Instant timestamp;
    private final String message;
    private final Boolean status;

    public LogEntry(int index, Instant timestamp, String message, Boolean status) {
        this.index = index;
        this.timestamp = timestamp;
        this.message = message;
        this.status = status;
    }

    public int getIndex() {
        return index;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public Boolean getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "main.java.LogEntry{index=" + index + ", timestamp=" + timestamp + ", message='" + message + "', status=" + status + "}";
    }
}

