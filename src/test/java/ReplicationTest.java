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
        transport.stopReordering("url-a");
    }

    @Test
    public void testDelayOnFollowerA() {
        transport.setDelay("url-a", 5000);
        startTime = System.currentTimeMillis();
        master.appendMsg("msg1", master.allWriteConcern());
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay >= 5000, "Expected at least 5000 ms, actual result: " + delay + "ms");
    }

    @Test
    public void testDelayOnFollowerB() {
        transport.setDelay("url-b", 6000);
        startTime = System.currentTimeMillis();
        master.appendMsg("msg2", master.allWriteConcern());
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay >= 6000, "Expected at least 6000 ms, actual result: " + delay + "ms");
    }

    @Test
    public void testNoDelayReplication() {
        startTime = System.currentTimeMillis();
        master.appendMsg("msg3", master.allWriteConcern());
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay < 1000, "Expected near-instant replication, actual result: " + delay + "ms");
    }

    @Test
    public void testFullReplicationAcrossAllNodes() {
        master.appendMsg("m4", master.allWriteConcern());
        master.appendMsg("m5", master.allWriteConcern());
        master.appendMsg("m6", master.allWriteConcern());

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
        master.appendMsg("msgParallel", master.allWriteConcern());
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        Assert.assertTrue(delay >= 3000, "Expected at least 3000ms (slowest follower), actual: " + delay + "ms");
        Assert.assertTrue(delay < 4000, "Expected less than 4000ms (would indicate sequential, not parallel), actual: " + delay + "ms");
    }

    @Test
    public void testReorderedMessagesAreOrderedOnFollower() throws InterruptedException {
        transport.reorderMessages("url-a", 2);

        master.appendMsg("r1", 1);
        master.appendMsg("r2", 1);

        Thread.sleep(500);

        List<LogEntry> masterLog = master.getLog();
        List<LogEntry> followerALog = followerA.getLog();

        Assert.assertEquals(followerALog.size(), masterLog.size(), "Follower A should have all entries");
        for (int i = 0; i < masterLog.size(); i++) {
            Assert.assertEquals(followerALog.get(i).getMessage(), masterLog.get(i).getMessage(),
                    "Entry at index " + i + " should be in the same order as on Master");
        }
    }

    @Test
    public void acceptanceTestForIteration2() throws InterruptedException {
        /**
         * Acceptance test:
         * 1.	Start M + S1 + S2 (delay 5-10 sec)
         * 2.	send (Msg1, W=1) - Ok
         * 3.	send (Msg2, W=2) - Ok
         * 4.	send (Msg3, W=3) - Wait because of S2 replication delay
         * 5.	send (Msg4, W=1) - Ok - should not be blocked by step 4 (the command may run in a different thread)
         * 6.	Check messages on S2 - [Msg1, (Msg2), (Msg3)]
         * 7.	Check messages on M|S1 - [Msg1, Msg2, Msg3, Msg4]
         */
        MockedTransport t = new MockedTransport();
        Master m = new Master("master", t);
        Follower fA = new Follower("fA", t);
        Follower fB = new Follower("fB", t);
        t.registerFollower("fA", fA);
        t.registerFollower("fB", fB);
        m.addFollower("fA");
        m.addFollower("fB");
        t.setDelay("fB", 5000);

        m.appendMsg("m1", 1);
        m.appendMsg("m2", 2);

        // HTTP client simulation
        Thread t3 = new Thread(() -> m.appendMsg("m3", 3));
        t3.start();
        Thread.sleep(200);

        long start = System.currentTimeMillis();
        m.appendMsg("m4", 1);
        long elapsed = System.currentTimeMillis() - start;
        Assert.assertTrue(elapsed < 1000, "m4 should not be blocked, took " + elapsed + "ms");

        // check messages on FB
        Assert.assertEquals(m.getLog().size(), 4);
        Assert.assertTrue(fB.getLog().size() < 4, "fB should still be behind");

        // wait for fB to finish
        t3.join();
        //Thread.sleep(1000);
        long deadline = System.currentTimeMillis() + 10000;
        while (fB.getLog().size() < 4 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        List<LogEntry> masterLog = m.getLog();
        List<LogEntry> fALog = fA.getLog();
        List<LogEntry> fBLog = fB.getLog();
        Assert.assertEquals(fALog.size(), masterLog.size(), "Follower A should have all entries");
        Assert.assertEquals(fBLog.size(), masterLog.size(), "Follower B should have all entries");
        for (int i = 0; i < masterLog.size(); i++) {
            Assert.assertEquals(fALog.get(i).getMessage(), masterLog.get(i).getMessage(), "Entry at index " + i + " should be in the same order as on Master");
            Assert.assertEquals(fBLog.get(i).getMessage(), masterLog.get(i).getMessage(), "Entry at index " + i + " should be in the same order as on Master");
        }
    }
}
