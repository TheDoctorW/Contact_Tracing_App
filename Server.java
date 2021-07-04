import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * server
 */
public class Server extends Thread {

    // global used informations
    static ServerSocket welcomeSocket;
    static int serverPort;
    static int blockDuration;
    static Hashtable<String, Integer> requestsTable;
    static List<String> credentials;
    static String credentialsFileName = "credentials.txt";
    static String tmpIDsFileName = "tempIDs.txt";
    static ReentrantLock syncLock = new ReentrantLock();
    static int generateIDDelay = 15 * 1000 * 60;
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    
    /**
     * main executable function
     * @param args command line inputs
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        
        // check if meet basic start inputs
        if (args.length != 2) {
            System.out.println("Please enter server port and block duration!");
            System.exit(-1);
        }

        // convert command line inputs to useful info
        serverPort = Integer.parseInt(args[0]);
        blockDuration = Integer.parseInt(args[1]) * 1000;

        // assign a new hashtable for counting user login attempts
        requestsTable = new Hashtable<String , Integer>();

        // create a new server socket to accept clients
        welcomeSocket = new ServerSocket(serverPort);
        System.out.println("Server is ready at " + serverPort + " , block duation time is " + blockDuration / 1000 + " seconds, standby!");

        // start generate tmpID for all users, do this every 15 mins
        writeTmpID();
        Timer gnerID = new Timer();
        gnerID.schedule(new GenerateID(), generateIDDelay);
        
        // create new thread to accept multiple clients 
        while (true) {
            Server svr = new Server();
            svr.start();
            Thread.sleep(1000);
        }

    }

    /**
     * actual running thread
     */
    public void run() {

        try {
            // accept new client request
            Socket conectionsSocket = welcomeSocket.accept();
            int check = 0;

            // convert message from client to userID, password and client choice
            String verifyInfo = (new BufferedReader(new InputStreamReader(conectionsSocket.getInputStream()))).readLine();
            String choice = verifyInfo.split(" ")[0];
            String userID = verifyInfo.split(" ")[1];
            String password = verifyInfo.split(" ")[2];
            String userIDPassword = userID + " " + password;

            if (choice.equals("register")) {
                // write new set of user information to credentials file
                System.out.println("Receive register request!");
                syncLock.lock();
                File file = new File(credentialsFileName);
                writeContent(file, userIDPassword);
                syncLock.unlock();
                System.out.println(userID + " register success, tmpID will be generated in next cycle!" + '\n');
                DataOutputStream registerInfo = new DataOutputStream(conectionsSocket.getOutputStream());
                registerInfo.writeBytes("Register success, please login again later!\n");

            } else {
                // add login attempts to hashtable
                addRequest(requestsTable, userID);
                System.out.println("Receive login request!");

                // do the authentication checking
                credentials = new ArrayList<String>();
                Scanner credentialsFile = new Scanner(new File(credentialsFileName));
                while (credentialsFile.hasNext()) {
                    credentials.add(credentialsFile.nextLine());
                }
                credentialsFile.close();

                for (String s : credentials) {
                    if (s.equals(userIDPassword)) {
                        check = 1;
                        requestsTable.put(userID, 0);
                        break;
                    }
                }
                if (check != 1) {
                    if (requestsTable.get(userID) == 3 || requestsTable.get(userID) == -1) {
                        check = -1;
                        requestsTable.put(userID, -1);
                    } else if (requestsTable.get(userID) == -2) {
                        check = -2;
                    }
                }

                if (check == 1) {
                    // authentication success, send welcome message to client
                    System.out.println(userID + " log in success!" + '\n');
                    DataOutputStream loginSuccessInfo = new DataOutputStream(conectionsSocket.getOutputStream());
                    loginSuccessInfo.writeBytes("Welcome to the BlueTrace Simulator!\n");

                    // start dealing with client command
                    while (true) {
                        // receive command from client
                        BufferedReader inFromClient = new BufferedReader(new InputStreamReader(conectionsSocket.getInputStream()));
                        String clientSentence;
                        System.out.println("Receiving command!");
                        clientSentence = inFromClient.readLine();
                        System.out.println(userID + ": " + clientSentence);
                        
                        if (clientSentence.equals("logout")) {
                            // receive logout command from client
                            syncLock.lock();
                            System.out.println("Receive logout request from " + userID + "!" + '\n');
                            // logout the client and break out from look to end this thread
                            conectionsSocket.close();
                            syncLock.unlock();
                            break;

                        } else if (clientSentence.equals("Download_tempID")) {
                            // receive download tmpID command from client 
                            syncLock.lock();
                            System.out.println("Receive download tempID request from " + userID + "!");
                            String outID = "Please give system some time to update your new id! (at most 15 mins)";
                            // scan the exist tmpID to find the match one (match userID and timemap)
                            List<String> tmpIDs = getTmpIDs();
                            for (String s : tmpIDs) {
                                String thisUser = s.split(" ")[0];
                                String thisID = s.split(" ")[1];
                                String tmpStartTime = s.split(" ")[2] + " " + s.split(" ")[3];
                                String tmpEndTime = s.split(" ")[4] + " " + s.split(" ")[5];
                                LocalDateTime thisStartTime = LocalDateTime.parse(tmpStartTime, formatter);
                                LocalDateTime thisEndTime = LocalDateTime.parse(tmpEndTime, formatter);
                                if (userID.equals(thisUser) && LocalDateTime.now().isAfter(thisStartTime) && LocalDateTime.now().isBefore(thisEndTime)) {
                                    outID = thisID + " " + thisStartTime.toString() + " " + thisEndTime.toString();
                                    break;
                                }
                            }
                            System.out.println(userID + ": " + outID + '\n');
                            outID = outID + '\n';
                            // send tmpID to client
                            DataOutputStream outToClient = new DataOutputStream(conectionsSocket.getOutputStream());
                            outToClient.writeBytes(outID);
                            syncLock.unlock();
                            
                        } else if (clientSentence.equals("Upload_contact_log")) {
                            // receive upload contact log command from client
                            syncLock.lock();
                            System.out.println("Receive upload contact log request from " + userID + "!");
                            // receive contact logs from client and store if for later use
                            BufferedReader logsFromClient = new BufferedReader(new InputStreamReader(conectionsSocket.getInputStream()));
                            int logSize = Integer.parseInt(logsFromClient.readLine());
                            List<String> logs = new ArrayList<String>();
                            for (int i = 0; i < logSize; i ++) {
                                logs.add(logsFromClient.readLine());
                            }
                            for (String log : logs) {
                                System.out.println(log);
                            }
                            // use stored contact logs to compare with tmpID list to do the checking
                            System.out.println("Start contact log checking!");
                            List<String> tmpIDs = getTmpIDs();
                            System.out.println("Potential infected users:");
                            for (String log : logs) {
                                for (String tmpID : tmpIDs) {
                                    if (log.equals(tmpID.split(" ")[1] + " " + tmpID.split(" ")[2] + " " + tmpID.split(" ")[3] + " " + tmpID.split(" ")[4] + " " + tmpID.split(" ")[5])) {
                                        System.out.println(tmpID.split(" ")[0] + " " + tmpID.split(" ")[2] + " " + tmpID.split(" ")[3] + " " + tmpID.split(" ")[4] + " " + tmpID.split(" ")[5]);
                                    }
                                }
                            }
                            System.out.println("");
                            // send successful message to client
                            DataOutputStream outToClient = new DataOutputStream(conectionsSocket.getOutputStream());
                            outToClient.writeBytes("Contact log received!\n");
                            syncLock.unlock();
                            
                        }
                    }

                } else if (check == -1) {
                    // authentication fail and fail too many times, block user for some time and send fail message to client
                    System.out.println("Block " + userID + "!" + '\n');
                    DataOutputStream loginFailInfo = new DataOutputStream(conectionsSocket.getOutputStream());
                    loginFailInfo.writeBytes("Invalid Password. Your account has been blocked. Please try again later!\n");
                    conectionsSocket.close();
                    requestsTable.put(userID, -2);
                    Timer timer = new Timer();
                    timer.schedule(new Block(requestsTable, userID), blockDuration);
                    conectionsSocket.close();
                
                } else if (check == -2) {
                    // user still been blocking, send fail message to client
                    System.out.println(userID + " on blocking!" + '\n');
                    DataOutputStream loginFailInfo = new DataOutputStream(conectionsSocket.getOutputStream());
                    loginFailInfo.writeBytes("Your account is blocked due to multiple login failures. Please try again later!\n");
                    conectionsSocket.close();

                } else {
                    // authentication fail, send fail message to client
                    System.out.println(userID + " log in fail!" + '\n');
                    DataOutputStream loginFailInfo = new DataOutputStream(conectionsSocket.getOutputStream());
                    loginFailInfo.writeBytes("Invalid Username or Password. Please try again!\n");
                    conectionsSocket.close();
                    
                }
            }
            conectionsSocket.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    /**
     * help function to add user login request to hashtable
     * @param requeststable hashtable
     * @param userID user ID
     */
    public static void addRequest(Hashtable<String, Integer> requeststable, String userID) {
        if (requeststable.containsKey(userID)) {
            int n = requeststable.get(userID);
            if (n >= 3) {
                n = 3;
            } else if (n >= 0) {
                n = n + 1;
            }
            requeststable.put(userID, n);
        } else {
            requeststable.put(userID, 1);
        }
    }

    /**
     * help function to write content to file
     * @param file file 
     * @param content content
     * @throws Exception
     */
    public static void writeContent(File file, String content) throws Exception {
        syncLock.lock();
        FileOutputStream fs = new FileOutputStream(file, true);
        content = content + '\n';
        byte[] contentInBytes = content.getBytes();
        fs.write(contentInBytes);
        fs.flush();
        fs.close();
        syncLock.unlock();
    }

    /**
     * help function to generate 20 bytes tmpID
     * @return tmpID
     */
    public static String generateTmpID() {
        Random rn = new Random();
        String id = new String();
        for (int i = 0; i < 20; i ++) {
        int n = rn.nextInt(10);
        id = id + n;
        }
        return id;
    }

    /**
     * help function to write gnerated tmpID with timemap to file
     */
    public static void writeTmpID() {
        try {
            List<String> userIDs = new ArrayList<String>();
            Scanner credentialsFile = new Scanner(new File(credentialsFileName));
            while(credentialsFile.hasNext()) {
                userIDs.add(credentialsFile.nextLine().split(" ")[0]);
            }
            credentialsFile.close();
            File tmpIDFile = new File(tmpIDsFileName);
            for (String id : userIDs) {
                String tmp = generateTmpID();
                String startTime = LocalDateTime.now().withNano(0).format(formatter);
                String endTime = LocalDateTime.now().plusMinutes(15).withNano(0).format(formatter);
                String writeIn = id + " " + tmp + " " + startTime + " " + endTime;
                writeContent(tmpIDFile, writeIn);
            }
        } catch (Exception e) {
            e.printStackTrace();;
        }
    }

    /**
     * help function to get full list of all exsit tmpIDs
     * @return list of tmpID
     * @throws Exception
     */
    static public List<String> getTmpIDs() throws Exception {
        List<String> tmpIDs = new ArrayList<String>();
        Scanner tmpIDsFile = new Scanner(new File(tmpIDsFileName));
        while (tmpIDsFile.hasNext()) {
            tmpIDs.add(tmpIDsFile.nextLine());
        }
        tmpIDsFile.close();
        return tmpIDs;
    }

}