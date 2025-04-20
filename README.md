# Proxy Server – Aiven Arones  

A proxy server is a server that acts as an intermediary between a request made by a client, and a particular server for a service or request for a resource. A Web Proxy is a local server that fetches items from the Web on behalf of a client, allowing for the blocking of pages or page caching. 

My program is able to: 

Respond to HTTP & HTTPS requests and display each request on a console. 

Dynamically block selected URLs via a management console 

Handle multiple requests simultaneously by implementing a threaded server 

High-Level Design 

My program is written in Java and implements socket programming by making use of the Socket and ServerSocket Java libraries. A “socket” is an endpoint for sending and receiving data across a network. My program forwards HTTP requests to the web server using sockets and input/output streams. My program is configured to run locally on port 4000, localhost:4000. 

When a client connection is accepted, a new socket instance is created to handle the client request. A new thread is then started to execute a client handler. By creating a new thread each time a client connection is accepted, my program allows for multiple client connections to be handled simultaneously. This means my program is a threaded server. 

The server uses sockets to listen to selected ports and facilitate client request handling. Client requests are handled using input and output streams that listen to select sockets. This allows for each HTTP and HTTPS request to be displayed on a management console by reading a client’s input stream using a BufferedReader. This reader reads each line and displays the request to the console.  

The received request is then split into its method (GET, CONNECT) and a URL. From the URL, the host and port can be obtained and used to check if the URL is blocked. Blocked URLs are stored in a thread safe arraylist using Collections.synchronizedList.  If the URL is HTTP, then the entire URL including the path is searched for within the list of blocked URLs. This is because for HTTP requests, the full request is visible to the proxy. If it is HTTPS, then only the host is used because when dealing with HTTPS traffic the proxy cannot see the full URL due to HTTPS encryption. 

The CONNECT method requests that a proxy establish a HTTPS tunnel to a destination server. If successful, it blindly forwards data in both directions until the tunnel is closed. If the method of the request is a CONNECT method, then my program will handle the HTTPS tunnelling. This is done by creating a new input and output stream to the target server. The transferData method takes an input and output stream and transfers data between two sockets. It is used to forward data in both directions from the client to the target.  

When first starting the program, a console manager thread is started to handle console commands for blocking and unblocking URLs. 

Block a URL using /block <URL> 

Unblock a URL using /unblock <URL> 

List blocked URLs using /list 

The above commands are used to block and unblock URLs. The console manager thread is constantly running and waits for an input. When using “/block”, if the URL starts with “http://” then the entire URL including the path is used. If the URL starts with “https://” then only the host is used for blocking. 

A buffer is used to store data for the transfer of data between two sockets. 

HTTP Response codes are used in my code, I use Response codes HTTP 200 (OK), 400 (Bad Request) and 403 (Forbidden).



