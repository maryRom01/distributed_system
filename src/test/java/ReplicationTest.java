import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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

    // iteration 3
    @Test
    public void testDeduplicationAfterRetry() {
        transport.failAfterApply("url-a", 1);

        startTime = System.currentTimeMillis();
        master.appendMsg("d1", master.allWriteConcern());   // чекаємо обох, тож retry для A завершиться до кінця виклику
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;

        //check for retry
        Assert.assertTrue(delay >= 400, "Expected a retry (>= 400 ms), actual result: " + delay + "ms");

        List<LogEntry> masterLog = master.getLog();
        List<LogEntry> followerALog = followerA.getLog();

        // there should be only 1 message
        Assert.assertEquals(followerALog.size(), masterLog.size(), "Follower A should have no extra entries");
        long count = followerALog.stream().filter(e -> e.getMessage().equals("d1")).count();
        Assert.assertEquals(count, 1, "d1 must be present exactly once on Follower A");

        // check indexes
        for (int i = 0; i < followerALog.size(); i++) {
            Assert.assertEquals(followerALog.get(i).getIndex(), i, "Index at position " + i + " should be " + i);
        }
    }

    @Test
    public void testFollowerDoesNotShowMessageUntilGapIsFilled() {
        Follower f = new Follower("f-order", transport);

        f.replicateMsg(new LogEntry(0, "t", "msg1", null));
        f.replicateMsg(new LogEntry(1, "t", "msg2", null));
        f.replicateMsg(new LogEntry(3, "t", "msg4", null));   // msg3 ще не прийшло

        Assert.assertEquals(f.getLog().size(), 2, "msg4 must not be visible until msg3 arrives");

        f.replicateMsg(new LogEntry(2, "t", "msg3", null));   // прогалина закрита

        List<String> messages = f.getLog().stream().map(LogEntry::getMessage).collect(Collectors.toList());
        Assert.assertEquals(messages, Arrays.asList("msg1", "msg2", "msg3", "msg4"));
    }

    @Test
    public void selfCheckAcceptanceTestForIteration3() throws InterruptedException {
        /**
         * 1. Start M + S1
         * 2. send (Msg1, W=1) - Ok
         * 3. send (Msg2, W=2) - Ok
         * 4. send (Msg3, W=3) - Wait
         * 5. send (Msg4, W=1) - Ok
         * 6. Start S2
         * 7. Check messages on S2 - [Msg1, Msg2, Msg3, Msg4]
         */
        MockedTransport t = new MockedTransport();
        Master m = new Master("master", t);
        Follower s1 = new Follower("s1", t);
        Follower s2 = new Follower("s2", t);
        t.registerFollower("s1", s1);
        t.registerFollower("s2", s2);
        m.addFollower("s1");
        m.addFollower("s2");

        t.setUnavailable("s2", true);   // S2 is unavailable
        m.appendMsg("Msg1", 1);
        m.appendMsg("Msg2", 2);

        // Simulation: Msg 3 must wait, because S2 is unavailable
        Thread t3 = new Thread(() -> m.appendMsg("Msg3", 3));
        t3.start();
        Thread.sleep(1000);
        Assert.assertTrue(t3.isAlive(), "Msg3 (w=3) should be blocked while S2 is down");

        // Msg4 ( w=1) not blocked even if Msg3 is not delivered
        startTime = System.currentTimeMillis();
        m.appendMsg("Msg4", 1);
        endTime = System.currentTimeMillis();
        delay = endTime - startTime;
        Assert.assertTrue(delay < 1000, "Msg4 should not be blocked, took " + delay + "ms");

        Assert.assertEquals(s2.getLog().size(), 0, "S2 is down, no data yet");

        // Start S2
        t.setUnavailable("s2", false);

        // retry should work, wait
        t3.join(20000);
        Assert.assertFalse(t3.isAlive(), "Msg3 should be released after S2 is back");

        long deadline = System.currentTimeMillis() + 20000;
        while (s2.getLog().size() < 4 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }

        List<String> messages = s2.getLog().stream().map(LogEntry::getMessage).collect(Collectors.toList());
        Assert.assertEquals(messages, Arrays.asList("Msg1", "Msg2", "Msg3", "Msg4"));
        System.out.println(messages);
    }

    @Test
    public void testHeartbeatDetectsDownAndRecovery() throws InterruptedException {
        MockedTransport t = new MockedTransport();
        t.registerFollower("s1", new Follower("s1", t));
        Master m = new Master("m", t);
        m.addFollower("s1");
        m.startHeartbeats(100);

        waitForStatus(m, "s1", "HEALTHY");
        t.setUnavailable("s1", true);
        waitForStatus(m, "s1", "UNHEALTHY");
        t.setUnavailable("s1", false);
        waitForStatus(m, "s1", "HEALTHY");
    }

    private void waitForStatus(Master m, String url, String expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (expected.equals(m.followerStatuses().get(url))) return;
            Thread.sleep(50);
        }
        Assert.fail("Expected " + expected + " but was " + m.followerStatuses().get(url));
    }
}
