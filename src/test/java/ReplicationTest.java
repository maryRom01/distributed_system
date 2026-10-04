import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.Assert;
import java.util.List;

public class ReplicationTest {
    private final MockedTransport transport = new MockedTransport();
    private final Master master = new Master("master", transport);
    private final Follower followerA = new Follower("follower-a", transport);
    private final Follower followerB = new Follower("follower-b", transport);
    private long startTime, endTime, delay;

    @BeforeTest
    public void setup() {
        String destA = "url-a";
        String destB = "url-b";
        transport.registerFollower(destA, followerA);
        transport.registerFollower(destB, followerB);
        master.addFollower(destA);
        master.addFollower(destB);
    }

    @BeforeMethod
    public void cleanUp() {
        transport.removeDelay("url-a");
        transport.removeDelay("url-b");
    }

    @Test
    public void testDelayOnFollowerA() {
        transport.setDelay("url-a", 5000);
        startTime = System.currentTimeMillis();
        master.appendMsg("msg1");
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay >= 5000, "Expected at least 5000 ms, actual result: " + delay + "ms");
    }

    @Test
    public void testDelayOnFollowerB() {
        transport.setDelay("url-b", 6000);
        startTime = System.currentTimeMillis();
        master.appendMsg("msg2");
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay >= 6000, "Expected at least 6000 ms, actual result: " + delay + "ms");
    }

    @Test
    public void testNoDelayReplication() {
        startTime = System.currentTimeMillis();
        master.appendMsg("msg3");
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay < 1000, "Expected near-instant replication, actual result: " + delay + "ms");
    }

    @Test
    public void testFullReplicationAcrossAllNodes() {
        master.appendMsg("m4");
        master.appendMsg("m5");
        master.appendMsg("m6");

        List<LogEntry> masterLog = master.getLog();
        List<LogEntry> followerALog = followerA.getLog();
        List<LogEntry> followerBLog = followerB.getLog();

        Assert.assertEquals(followerALog.size(), masterLog.size(), "Follower A log size should match Master's");
        Assert.assertEquals(followerBLog.size(), masterLog.size(), "Follower B log size should match Master's");

        for (int i = 0; i < masterLog.size(); i++) {
            String masterMsg = masterLog.get(i).getMessage();
            Assert.assertEquals(followerALog.get(i).getMessage(), masterMsg,
                    "Follower A entry at index " + i + " should match Master's");
            Assert.assertEquals(followerBLog.get(i).getMessage(), masterMsg,
                    "Follower B entry at index " + i + " should match Master's");
        }
    }

    @Test
    public void testParallelReplicationTiming() {
        transport.setDelay("url-a", 1000);
        transport.setDelay("url-b", 3000);

        startTime = System.currentTimeMillis();
        master.appendMsg("msgParallel");
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay >= 3000, "Expected at least 3000ms (slowest follower), actual: " + delay + "ms");
        Assert.assertTrue(delay < 4000, "Expected less than 4000ms (would indicate sequential, not parallel), actual: " + delay + "ms");
    }
}
