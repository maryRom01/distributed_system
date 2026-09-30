import java.util.ArrayList;
import java.util.List;

abstract class Node {
    List<LogEntry> log;
    String id;

    Node(String id) {
        this.id = id;
        this.log = new ArrayList<>();
    }

    void listMsg() {
        for (LogEntry logEntry : log) {
            System.out.println("main.java.Node with id: " +  this.id + " " + logEntry);
        }
    }


}

