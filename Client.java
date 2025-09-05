import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client implements Runnable{

    private Socket client;
    private BufferedReader in;
    private PrintWriter out;
    private AtomicBoolean done = new AtomicBoolean(false);
    private List<String> messageHistory = Collections.synchronizedList(new ArrayList<>());
    private StringBuilder currentInput = new StringBuilder();
    private int scrollOffset = 0;
    private int terminalHeight = 24;  // Default terminal height
    private int terminalWidth = 80;   // Default terminal width

    // ANSI escape codes
    private static final String CLEAR_SCREEN = "\033[2J";
    private static final String MOVE_CURSOR_HOME = "\033[H";
    private static final String MOVE_CURSOR_TO = "\033[%d;%dH";
    private static final String CLEAR_LINE = "\033[2K";
    private static final String SAVE_CURSOR = "\033[s";
    private static final String RESTORE_CURSOR = "\033[u";
    private static final String HIDE_CURSOR = "\033[?25l";
    private static final String SHOW_CURSOR = "\033[?25h";

    @Override
    public void run() {
        try {
            // Try to get actual terminal size
            updateTerminalSize();
            
            // Setup terminal
            setupTerminal();
            
            addMessage("Connecting to server...");
            redrawScreen();
            
            System.out.println("Connecting to server...");
            client = new Socket("35.231.182.9", 12345);
            addMessage("Connected!");
            redrawScreen();
            
            out = new PrintWriter(client.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));

            InputHandler inHandler = new InputHandler();
            Thread t = new Thread(inHandler);
            t.start();

            String inMessage;
            while((inMessage = in.readLine()) != null) {
                addMessage(inMessage);
                redrawScreen();
            }
        } catch (IOException e) {
            shutdown();
        }
    }

    private void updateTerminalSize() {
        // Try to get terminal size using system commands
        try {
            ProcessBuilder pb = new ProcessBuilder();
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows - not as reliable, use defaults
                terminalHeight = 24;
                terminalWidth = 80;
            } else {
                // Unix-like systems
                pb.command("stty", "size");
                pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                Process process = pb.start();
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine();
                if (result != null && !result.trim().isEmpty()) {
                    String[] parts = result.trim().split(" ");
                    if (parts.length == 2) {
                        terminalHeight = Integer.parseInt(parts[0]);
                        terminalWidth = Integer.parseInt(parts[1]);
                    }
                }
                process.waitFor();
                reader.close();
            }
        } catch (Exception e) {
            // Use defaults if detection fails
            terminalHeight = 24;
            terminalWidth = 80;
        }
    }

    private void setupTerminal() {
        // Enable raw mode for better input handling on Unix systems
        try {
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("stty", "-icanon", "min", "1");
                pb.inheritIO();
                pb.start().waitFor();
            }
        } catch (Exception e) {
            // Continue with normal mode if raw mode setup fails
        }
        
        System.out.print(CLEAR_SCREEN + MOVE_CURSOR_HOME + SHOW_CURSOR);
        System.out.flush();
    }

    private void restoreTerminal() {
        try {
            if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                ProcessBuilder pb = new ProcessBuilder("stty", "icanon");
                pb.inheritIO();
                pb.start().waitFor();
            }
        } catch (Exception e) {
            // Ignore
        }
        
        System.out.print(CLEAR_SCREEN + MOVE_CURSOR_HOME + SHOW_CURSOR);
        System.out.flush();
    }

    private void addMessage(String message) {
        messageHistory.add(message);
        // Keep only last 1000 messages
        if (messageHistory.size() > 1000) {
            messageHistory.remove(0);
        }
        // Auto-scroll to bottom when new message arrives
        scrollOffset = 0;
    }

    private void redrawScreen() {
        StringBuilder screen = new StringBuilder();
        
        // Clear screen and move to top
        screen.append(CLEAR_SCREEN).append(MOVE_CURSOR_HOME);
        
        // Calculate message display area (leave 3 rows for input section)
        int messageAreaHeight = terminalHeight - 3;
        int totalMessages = messageHistory.size();
        
        // Calculate which messages to show based on scroll
        int startIndex = Math.max(0, totalMessages - messageAreaHeight - scrollOffset);
        int endIndex = Math.min(totalMessages, startIndex + messageAreaHeight);
        
        // Display messages
        synchronized (messageHistory) {
            for (int i = startIndex; i < endIndex; i++) {
                String msg = messageHistory.get(i);
                // Simple message truncation for now
                if (msg.length() > terminalWidth) {
                    msg = msg.substring(0, terminalWidth - 3) + "...";
                }
                screen.append(msg);
                if (i < endIndex - 1) {
                    screen.append("\n");
                }
            }
        }
        
        // Fill remaining lines in message area
        int linesUsed = endIndex - startIndex;
        for (int i = linesUsed; i < messageAreaHeight; i++) {
            screen.append("\n");
        }
        
        // Draw separator line
        screen.append("\n");
        for (int i = 0; i < terminalWidth; i++) {
            screen.append("─");
        }
        
        // Draw scroll indicator if needed
        if (scrollOffset > 0) {
            String scrollIndicator = "[↑" + scrollOffset + " more]";
            int indicatorPos = terminalWidth - scrollIndicator.length();
            screen.append(String.format(MOVE_CURSOR_TO, terminalHeight - 2, indicatorPos));
            screen.append(scrollIndicator);
        }
        
        // Move to input line and draw prompt
        screen.append(String.format(MOVE_CURSOR_TO, terminalHeight, 1));
        screen.append("Message: ");
        
        // Draw current input
        String inputText = currentInput.toString();
        int maxInputWidth = terminalWidth - 9; // Account for "Message: "
        if (inputText.length() <= maxInputWidth) {
            screen.append(inputText);
        } else {
            // Show the end of long input
            screen.append(inputText.substring(inputText.length() - maxInputWidth));
        }
        
        System.out.print(screen.toString());
        System.out.flush();
    }

    private void scrollUp() {
        int messageAreaHeight = terminalHeight - 3;
        if (scrollOffset < messageHistory.size() - messageAreaHeight) {
            scrollOffset++;
            redrawScreen();
        }
    }

    private void scrollDown() {
        if (scrollOffset > 0) {
            scrollOffset--;
            redrawScreen();
        }
    }

    public void shutdown() {
        if (done.getAndSet(true)) {
            return;
        }
        
        restoreTerminal();
        
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
                int ch;
                while (!done.get()) {
                    ch = System.in.read();
                    
                    if (ch == -1) break; // EOF
                    
                    if (ch == 27) { // ESC sequence
                        // Handle arrow keys and other escape sequences
                        System.in.mark(10);
                        int next1 = System.in.read();
                        if (next1 == 91) { // '[' - ANSI escape sequence
                            int next2 = System.in.read();
                            if (next2 == 65) { // Up arrow
                                scrollUp();
                                continue;
                            } else if (next2 == 66) { // Down arrow
                                scrollDown();
                                continue;
                            }
                        }
                        System.in.reset();
                        // ESC pressed alone - clear input
                        currentInput.setLength(0);
                        redrawScreen();
                        continue;
                    }
                    
                    if (ch == 13 || ch == 10) { // Enter
                        String message = currentInput.toString().trim();
                        currentInput.setLength(0);
                        
                        if(message.equals("/quit")) {
                            out.println(message);
                            shutdown();
                            break;
                        } else if(message.startsWith("/nick ")) {
                            out.println(message);
                            addMessage("You changed your nickname");
                        } else if(!message.isEmpty()) {
                            out.println(message);
                            addMessage("You: " + message);
                        }
                        redrawScreen();
                        
                    } else if (ch == 8 || ch == 127) { // Backspace or DEL
                        if (currentInput.length() > 0) {
                            currentInput.setLength(currentInput.length() - 1);
                            redrawScreen();
                        }
                        
                    } else if (ch >= 32 && ch <= 126) { // Printable characters
                        currentInput.append((char) ch);
                        redrawScreen();
                    }
                }
            } catch (IOException e) {
                shutdown();
            }
        }
        
    }

    public static void main(String[] args) {
        Client client = new Client();
        
        // Add shutdown hook to restore terminal
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.shutdown();
        }));
        
        Thread t = new Thread(client);
        t.start();
        try {
            t.join();  // Wait for client thread to finish
        } catch (InterruptedException e) {
            client.shutdown();
        }
    }

}