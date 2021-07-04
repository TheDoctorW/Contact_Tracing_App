import java.io.*;
import java.net.*;
import java.time.*;
import java.util.*;

/** 
 * client
 */
public class Client {

    // global used informations
    static Socket requestSocket;
    static String logFileName = "<z5175023>_contactlog.txt";

    /**
     * main executable function
     * @param args command line inputs
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        // check if meet basic start inputs
        if (args.length != 3) {
            System.out.println("Please enter server IP and server port and client UDP port!");
            System.exit(-1);
        }

        // convert command line inputs to useful info
        InetAddress serverIP = InetAddress.getByName(args[0]);
        int serverPort = Integer.parseInt(args[1]);
        int clientUDPPort = Integer.parseInt(args[2]);

        // string to store full info for tmpID for later use
        String myTmpId = new String();

        // authentication process
        while (true) {
            // send new socket to server
            requestSocket = new Socket(serverIP, serverPort);

            // do login or register
            System.out.println("Register or Login?");
            String choice;
            BufferedReader inchoice = new BufferedReader(new InputStreamReader(System.in));
            choice = inchoice.readLine();

            if (!choice.equals("register") && !choice.equals("login")) {
                System.out.println("Wrong command, please try again!");
                continue;
            }
            
            System.out.print("Username: ");
            String username;
		    BufferedReader inUsername = new BufferedReader(new InputStreamReader(System.in));
            username = inUsername.readLine();
            if (username.equals("")) {
                System.out.println("Error. Please type something!");
            }

            System.out.print("Password: ");
            String password;
            BufferedReader inPassword = new BufferedReader(new InputStreamReader(System.in));
            password = inPassword.readLine();
            if (password.equals("")) {
                System.out.println("Error. Please type something!");
            }

            // send request to server
            String request = new String();
            request = choice + " " + username + " " + password;
            
            DataOutputStream outRequest = new DataOutputStream(requestSocket.getOutputStream());
            outRequest.writeBytes(request + '\n');

            // receive message from server
            BufferedReader inRequest = new BufferedReader(new InputStreamReader(requestSocket.getInputStream()));
		    String requestFromServer;
            requestFromServer = inRequest.readLine();
            System.out.println(requestFromServer + '\n');

            if (requestFromServer.equals("Welcome to the BlueTrace Simulator!")) {
                // successful login, break out from authentication process
                break;

            } else if (requestFromServer.equals("Invalid Password. Your account has been blocked. Please try again later!") || requestFromServer.equals("Your account is blocked due to multiple login failures. Please try again later!")) {
                // been blocked and shut down the client
                requestSocket.close();
                System.exit(-1);

            } else if (requestFromServer.equals("Register success, please login again later!")) {
                // login fail, prepare to send new set of message
                requestSocket.close();
                continue;
            }

            // close this request socket
            requestSocket.close();
        }

        // executing command process
        while (true) {

            System.out.print("Please type command: ");

            String command;
            BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in));
            command = inFromUser.readLine();

            // print out error message for invalid command
            if (!command.equals("Download_tempID") && !command.equals("Upload_contact_log") && !command.equals("logout") && !command.split(" ")[0].equals("Beacon")) {
                System.out.println("Error. Invalid command, please try again!" + '\n');
                continue;
            }

            // send command to server
            DataOutputStream outToServer = new DataOutputStream(requestSocket.getOutputStream());
            outToServer.writeBytes(command + '\n');
            
            if (command.equals("Upload_contact_log")) {
                // scan the contact log file and  send it to server
                List<String> logs = new ArrayList<String>();
                Scanner logFile = new Scanner(new File(logFileName));
                while (logFile.hasNext()) {
                    logs.add(logFile.nextLine());
                }
                String out = Integer.toString(logs.size()) + '\n';
                for (String s : logs) {
                    out = out + s + '\n';
                }
                System.out.print(out);
                DataOutputStream contactLogs = new DataOutputStream(requestSocket.getOutputStream());
                contactLogs.writeBytes(out);
            }
            
            // receive message from server
            BufferedReader inFromServer = new BufferedReader(new InputStreamReader(requestSocket.getInputStream()));
            String sentenceFromServer;
            sentenceFromServer = inFromServer.readLine();

            if (command.equals("logout")) {
                // close the socket and shut down the client when log out
                requestSocket.close();
                System.exit(0);
            } else if (command.equals("Download_tempID")) {
                // store the full info of tmpID and process print version
                myTmpId = sentenceFromServer;
                sentenceFromServer = sentenceFromServer.split(" ")[0];
            }
            
            // print out message from server
            System.out.println("From Server: " + sentenceFromServer + '\n');
        }

    }
    
}