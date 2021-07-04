import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;

/**
 * do the user block job
 */
public class Block extends TimerTask {

    private Hashtable<String, Integer> requestsTable;
    private String userID;

    public Block(Hashtable<String, Integer> requestsTable, String userID) {
        this.requestsTable = requestsTable;
        this.userID = userID;
    }

    @Override
    public void run() {
        // set user login attempt times back to 0
        if (requestsTable.get(userID) == -1) {
            requestsTable.put(userID, 0);
        }
    }
    
}