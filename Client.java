import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client implements Runnable{

    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private volatile boolean done;

    @Override
    public void run() {
        try {
            System.out.println("Connecting to server...");
            client = new Socket("127.0.0.1", 12345);
            System.out.println("Connected!");
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            String inMessage;
            while((inMessage = in.readLine()) != null) {
                System.out.println(inMessage);
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    public void shutdown() {
        done = true;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (client != null && !client.isClosed()) {
                client.close();
            }
        } catch (Exception e) {
            // ignore
        }
    }


    class InputHandler implements Runnable {

        @Override
        public void run() {
            try {
                BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));

                while(!done) {
                    String message = inReader.readLine();
                    if(message.trim().equals("/quit")) {
                        out.println(message);
                        inReader.close();
                        shutdown();
                    } else {
                        out.println(message);
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }
        
    }

    public static void main(String[] args) {
        Client client = new Client();
        Thread t = new Thread(client);
        t.start();
        try {
            t.join();  // Wait for client thread to finish
        } catch (InterruptedException e) {
            //ignore
        }
    }

}
