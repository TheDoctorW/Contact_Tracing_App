import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;

/**
 * do the tmpID generating job
 */
public class GenerateID extends TimerTask {

    @Override
    public void run() {
        // generate new set of tmpID to all users and start a new delay cycle
        Server.writeTmpID();
        Timer timer = new Timer();
        timer.schedule(new GenerateID(), Server.generateIDDelay);
    }
    
}