import java.io.*;
import java.net.*;
import java.util.Collections;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.net.ServerSocket;
import java.net.Socket;


// What is a Socket?
// A socket is essentially an endpoint for communication between machines.
// The java.net.Socket class implements the client side of a socket connection, while the java.net.ServerSocket class implements the server side. -- kusal gunasekara

// Proxy Server Class
public class ProxyServer {
    private static final int PORT = 4000; // Port number for the proxy server
    static int defaultHttpPort = 80;

    // create thread safe arraylist
    static List<String> normalList = new ArrayList<>();
    static List<String> blockedUrls = Collections.synchronizedList(normalList);

    public static void main(String[] args) {

        try {
            // start server and bint to port 4000
            System.out.println("Starting server...");
            ServerSocket ss = new ServerSocket(PORT); // Create a ServerSocket to listen to incoming client connections at port 4000

            System.out.println("Now serving at <http://localhost>:" + PORT);

            Thread consoleManager = new Thread(ProxyServer::manageConsoleCommands); // Start a thread to handle console commands for blocking/unblocking URLs
            consoleManager.start(); // Start consoleManager thread


            // loop to wait for clients
            while (true) {

                Socket s = ss.accept(); // Accept a connection from a client
                System.out.println("Client connected " + s.getInetAddress());

                Thread thread= new Thread(() -> handleClientRequest(s)); // Create new thread to handle each client request using the client socket as parameter
                thread.start(); // Start thread
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    // Method 1 - console manager for blocking and unblocking
    private static void manageConsoleCommands() {
        Scanner scanner = new Scanner(System.in);

        // while loop to constantly check for input commands
        while (true) {
            System.out.println("/block <URL> to block\n/unblock <URL> to unblock\n/list to show the list of blocked sites:");

            String input = scanner.nextLine().trim().toLowerCase(); // get input

            // block command
            if (input.startsWith("/block ")) {
                String url = input.substring(6).trim();
                try {
                    URI uri = new URI(url);
                    String hostToBlock = uri.getHost();

                    // Case 1 - http (block url and path)
                    if (url.startsWith("http://")) {
                        // Block full URL if HTTP
                        if (blockedUrls.contains(url)!=true) {
                            blockedUrls.add(url);
                            System.out.println("Blocked HTTP URL: " + url);
                        }
                    }

                    // Case 2 - https (block host only)
                    if (url.startsWith("https://")&&hostToBlock != null) {
                        // Block only first part if HTTPS
                        if (blockedUrls.contains(hostToBlock.toLowerCase())!=true) {
                            blockedUrls.add(hostToBlock.toLowerCase());
                            System.out.println("Blocked domain: " + hostToBlock);
                        }
                    } else {
                        // System.out.println("Invalid or already blocked URL.");
                    }


                } catch (Exception e) {
                    System.out.println("Invalid URL.");
                }

            }

            // unblock command
            if (input.startsWith("/unblock ")) {
                String url = input.substring(8).trim(); // isolate url

                if (blockedUrls.contains(url)) {
                    blockedUrls.remove(url);
                    System.out.println("Unblocked: " + url);
                } else {
                    System.out.println("URL not found in block list");
                }

            }

            // list command
            if (input.contains("/list")) {
                System.out.println("Blocked URLs: " + blockedUrls);
            } else {
                System.out.println("Unknown command. Use '/block <URL>', '/unblock <URL>', or '/list'");
            }
        }
    }


    // Method 2 to handle client requests
    private static void handleClientRequest(Socket clientSocket) {

        // set up input and output streams of the client socket to read the client's request and send the response
        try (
                // client input and output streams
                InputStream clientIn = clientSocket.getInputStream();
                OutputStream clientOut = clientSocket.getOutputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientIn)); // used to read client requests
                PrintWriter writer = new PrintWriter(clientOut, true) // used to write response
        ) {

            // create buffer to store data
            byte[] buffer = new byte[4096];
            int bytesRead;

            // Read client request
            String requestLine = reader.readLine();

            if (requestLine == null){
                return; // should not be null, no request
            }

            System.out.println("Received request: " + requestLine); // Display request onto management console

            // split the request into two parts
            String[] requestParts = requestLine.split(" ");

            if (requestParts.length < 2){
                return;
            }

            String method = requestParts[0]; // GET, POST, CONNECT (if HTTPS)
            String url = requestParts[1];    // Requested URL

            // Extract host and port
            String host = null;
            int port = defaultHttpPort; // Default HTTP port (will be 80 if not specified)

            // reads
            String line;
            while (!(line = reader.readLine()).isEmpty()&&line!=null) {
                //System.out.println("line: " + line);
                if (line.startsWith("Host:")) {

                    // get host
                    host = line.split(" ")[1];

                    // get port if specified
                    if (host.contains(":")) {
                        String[] hostParts = host.split(":");
                        host = hostParts[0];
                        port = Integer.parseInt(hostParts[1]);
                    }
                }
            }



            if (host == null) {
                writer.println("HTTP/1.1 400 Bad Request\r\n\r\nMissing Host Header");
                return;
            }
            //String normalizedHost = host.toLowerCase();

            // check if url is blocked

            // case 1 - http
            if (url.startsWith("http://")) {
                // Block full HTTP URL if it's in the blocked list
                if (blockedUrls.contains(url)) {
                    writer.println("HTTP/1.1 403 Forbidden\r\n\r\nBlocked by Proxy");
                    System.out.println("Blocked full HTTP request: " + url);
                    return;
                }
            }

            // case 2 - https

            else {
                // Block HTTPS requests by hostname
                if (blockedUrls.contains(host)||blockedUrls.contains(url)) {
                    //System.out.println("Blocked HTTPS URL: " + url);
                    writer.println("HTTP/1.1 403 Forbidden\r\n\r\nBlocked by Proxy");
                    System.out.println("Blocked access to: " + host);
                return;
                }
            }

            System.out.println("Method: " + method+", URL: " + url+", Host: " + host);

            // Handle HTTPS tunneling (CONNECT method)
            if (method.equalsIgnoreCase("CONNECT")&&url.startsWith("https://")!=true) {

                try (Socket targetSocket = new Socket(host, port);
                     InputStream targetIn = targetSocket.getInputStream();
                     OutputStream targetOut = targetSocket.getOutputStream()) {

                    // Send HTTP 200 Connection Established response
                    clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
                    clientOut.flush();

                    // transfer data between client and target server using threads
                    Thread clientToTarget = new Thread(() -> transferData(clientIn, targetOut));
                    Thread targetToClient = new Thread(() -> transferData(targetIn, clientOut));

                    clientToTarget.start();
                    targetToClient.start();

                    clientToTarget.join();
                    targetToClient.join();
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
                return;
            }


            // Forward HTTP request to the actual web server
            try (Socket serverSocket = new Socket(host, port);
                 OutputStream serverOut = serverSocket.getOutputStream();
                 InputStream serverIn = serverSocket.getInputStream();
                 PrintWriter serverWriter = new PrintWriter(serverOut, true)) {

    serverWriter.println(requestLine); // Send first request line

    // Forward lines
    while (!(line = reader.readLine()).isEmpty()) {
        serverWriter.println(line);
    }
    serverWriter.println();
    //serverWriter.flush();

    // Forward server response to client
    while ((bytesRead = serverIn.read(buffer)) != -1) {
        clientOut.write(buffer, 0, bytesRead);
        clientOut.flush();
    }


    // close the output streams
    clientOut.close();
    serverOut.close();

    // close sockets
    clientSocket.close();
    serverSocket.close();

}

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // method 3 - transfers data between two sockets for https
    private static void transferData(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }
}
