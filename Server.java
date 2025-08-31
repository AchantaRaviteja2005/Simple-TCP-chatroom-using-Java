import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server implements Runnable {

    private List<ConnectionHandler> connections = Collections.synchronizedList(new ArrayList<>());
    private ServerSocket server;
    private boolean done;
    private ExecutorService pool;

    // ANSI colors
    private static final String[] COLORS = {
        "\u001B[31m", // Red
        "\u001B[32m", // Green
        "\u001B[33m", // Yellow
        "\u001B[34m", // Blue
        "\u001B[35m", // Magenta
        "\u001B[36m"  // Cyan
    };
    private static final String RESET = "\u001B[0m";

    public Server() {
        connections = new ArrayList<>();
        done = false;
    }

    @Override
    public void run() {
        try {
            System.out.println("Server starting on port 12345...");
            server = new ServerSocket(12345);
            pool = Executors.newCachedThreadPool();

            while (!done) {
                Socket client = server.accept();
                System.out.println("New client connected: " + client.getInetAddress());

                ConnectionHandler handler = new ConnectionHandler(client);
                connections.add(handler);
                pool.execute(handler);
            }
        } catch (Exception e) {
            System.err.println("Server error: " + e.getMessage());
            shutdown();
        }
    }

    // Send message to all clients, except sender (to avoid echo)
    public void broadcast(String message, ConnectionHandler sender) {
        for (ConnectionHandler ch : connections) {
            if (ch != null && ch != sender) {
                ch.sendMessage(message);
            }
        }
    }

    public void shutdown() {
        done = true;
        pool.shutdown();
        try {
            if (server != null && !server.isClosed()) {
                server.close();
            }
            for (ConnectionHandler ch : connections) {
                ch.shutdown();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    class ConnectionHandler implements Runnable {
        private Socket client;
        private BufferedReader in;
        private PrintWriter out;
        private String nickname;
        private String color;

        public ConnectionHandler(Socket client) {
            this.client = client;
            int idx = (int) (Math.random() * COLORS.length);
            this.color = COLORS[idx];
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(client.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(client.getInputStream()));

                sendMessage("Please enter a nickname: ");
                while (true) {
                    nickname = in.readLine();
                    if (nickname != null) {
                        nickname = nickname.trim();
                        if (!nickname.isBlank()) {
                            break;
                        }
                    }
                    sendMessage("Invalid nickname. Please enter a valid nickname: ");
                }

                System.out.println(nickname + " connected!");
                broadcast(color + nickname + RESET + " joined the chat!", this);

                String message;
                while ((message = in.readLine()) != null) {
                    if (message.startsWith("/nick ")) {
                        String[] messageSplit = message.split(" ", 2);
                        String newNick = messageSplit[1].trim();

                        if (!newNick.isBlank()) {
                            broadcast(color + nickname + RESET + " changed their nickname to " + color + newNick + RESET, this);
                            System.out.println(nickname + " changed to " + newNick);
                            nickname = newNick;
                            sendMessage("Successfully changed nickname to " + nickname);
                        } else {
                            sendMessage("Nickname cannot be empty. Usage: /nick <new_name>");
                        }

                    } else if (message.trim().startsWith("/quit")) {
                        System.out.println(nickname + " left!");
                        broadcast(color + nickname + RESET + " left the chat!", this);
                        shutdown();
                    } else {
                        broadcast(color + nickname + RESET + ": " + message, this);
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }

        public void shutdown() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (!client.isClosed()) {
                    client.close();
                }
                connections.remove(this);
            } catch (IOException e) {
                // ignore
            }
        }
    }

    public static void main(String[] args) {
        Server server = new Server();
        server.run();
        System.out.println("Server has stopped.");
    }
}
