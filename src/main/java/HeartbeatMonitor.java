import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class HeartbeatMonitor {

    public enum Status { HEALTHY, SUSPECTED, UNHEALTHY }

    private static final int UNHEALTHY_AFTER = 3;   // retry 3 times

    private final Logger logger = Logger.getLogger(HeartbeatMonitor.class.getName());
    private final Transport transport;
    private final Object lock = new Object();
    private final Map<String, Status> statuses = new HashMap<>();
    private final Map<String, Integer> failures = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4, r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    public HeartbeatMonitor(Transport transport) {
        this.transport = transport;
    }

    public void watch(String url, long intervalMs) {
        synchronized (lock) {
            statuses.put(url, Status.HEALTHY);
            failures.put(url, 0);
        }
        scheduler.scheduleWithFixedDelay(() -> check(url), 0, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void check(String url) {
        boolean ok;
        try {
            transport.ping(url);
            ok = true;
        } catch (Exception e) {
            ok = false;
        }
        synchronized (lock) {
            Status old = statuses.get(url);
            int count = ok ? 0 : failures.get(url) + 1;
            failures.put(url, count);
            Status now = ok ? Status.HEALTHY
                    : (count >= UNHEALTHY_AFTER ? Status.UNHEALTHY : Status.SUSPECTED);
            statuses.put(url, now);
            if (old != now) {
                logger.info(url + ": " + old + " -> " + now);
                lock.notifyAll();   // up all threads
            }
        }
    }

    /** Block a thread for UNHEALTHY */
    public void awaitAvailable(String url) throws InterruptedException {
        synchronized (lock) {
            while (statuses.getOrDefault(url, Status.HEALTHY) == Status.UNHEALTHY) {
                lock.wait();
            }
        }
    }

    public Map<String, String> snapshot() {
        synchronized (lock) {
            Map<String, String> copy = new HashMap<>();
            statuses.forEach((url, status) -> copy.put(url, status.name()));
            return copy;
        }
    }
}
