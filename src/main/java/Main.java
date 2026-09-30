import com.google.gson.Gson;

import java.time.Instant;

public class Main {
    public static void main(String[] args) {
        Master master = new Master("master-1");
        Follower follower1 = new Follower("follower-1");
        Follower follower2 = new Follower("follower-2");
        master.addFollower(follower1);
        master.addFollower(follower2);
        master.appendMsg("message lorem ipsum 1");
        master.listMsg();
        follower1.listMsg();
        follower2.listMsg();
        master.appendMsg("message lorem ipsum 2");
        master.listMsg();
        follower1.listMsg();
        follower2.listMsg();

        Gson gson = new Gson();
        String json = gson.toJson("hello");
        System.out.println(json);
    }
}

