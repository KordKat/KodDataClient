package hello1.koddata.client;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TerminalService {

    private Terminal terminal;
    private LineReader reader;
    private String terminalPrefix;

    private ServerService serverService;

    private Queue<String> printQueue;

    private volatile boolean running = false;

    private Thread printThread;
    private Thread readThread;

    public TerminalService(String terminalPrefix) throws IOException {
        this.terminalPrefix = terminalPrefix;
        this.printQueue = new ConcurrentLinkedQueue<>();
        this.printThread = new Thread(this::doPrint);
        this.readThread = new Thread(this::doRead);
        terminal = TerminalBuilder.builder().system(true).build();
        reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .highlighter(new SyntaxHighlighter())
                .build();
    }

    public void initServerService(ServerService service) {
        this.serverService = service;
    }

    public void start() {
        running = true;
        printThread.start();
        readThread.start();
    }

    private void doPrint() {
        try {
            while (running) {
                String msg = printQueue.poll();
                if (msg != null) {
                    reader.printAbove(msg);
                } else {
                    // Sleep briefly to avoid busy-waiting
                    Thread.sleep(50);
                }
            }
        } catch (InterruptedException e) {
            // Thread interrupted, exit gracefully
        }
    }

    private void doRead() {
        StringBuilder stringBuilder = new StringBuilder();

        while (running) {
            String prefix = stringBuilder.length() == 0 ? terminalPrefix : "... ";
            String in;
            try {
                in = reader.readLine(prefix);
            } catch (Exception e) {
                enqueueMessage("Error reading input: " + e.getMessage());
                break;
            }

            if (in == null || in.trim().equalsIgnoreCase("exit")) {
                break;  // exit the loop
            }

            stringBuilder.append(in).append("\n");

            if (in.contains(";")) {
                doFlush(stringBuilder.toString());
                stringBuilder.setLength(0);  // clear the builder
            }
        }
        running = false;
    }

    private void doFlush(String cmd) {
        if (cmd.toLowerCase().startsWith("upload")) {
            String pathStr = cmd.substring("upload".length() + 1, cmd.length() - 2).trim();

            try {
                Path path = Paths.get(pathStr);
                byte[] fileBytes = Files.readAllBytes(path);

                String filename = path.getFileName().toString();
                byte[] filenameBytes = filename.getBytes(StandardCharsets.UTF_8);

                int totalLength = 1 + 8 + filenameBytes.length + fileBytes.length;
                ByteBuffer buffer = ByteBuffer.allocate(totalLength);

                buffer.put((byte) 1);
                buffer.putInt(filenameBytes.length);
                buffer.put(filenameBytes);
                buffer.putInt(fileBytes.length);
                buffer.put(fileBytes);

                byte[] dataToSend = buffer.array();

                serverService.enqueueSend(dataToSend);
                enqueueMessage("Uploading file: " + filename);

            } catch (IOException e) {
                enqueueMessage("Failed to read file: " + pathStr + " (" + e.getMessage() + ")");
            }

        }else if (cmd.toLowerCase().startsWith("consult")) {
            String pathStr = cmd.substring("consult".length() + 1, cmd.length() - 2).trim();

            try {
                Path path = Paths.get(pathStr);
                byte[] fileBytes = Files.readAllBytes(path);

                String filename = path.getFileName().toString();
                enqueueMessage("Executing: " + filename);

                byte[] dataToSend = new byte[fileBytes.length + 1];
                dataToSend[0] = 0;
                System.arraycopy(fileBytes, 0, dataToSend, 1, fileBytes.length);

                serverService.enqueueSend(dataToSend);

            } catch (IOException e) {
                enqueueMessage("Failed to read file: " + pathStr + " (" + e.getMessage() + ")");
            }

        } else {
            byte[] cmdBytes = cmd.getBytes(StandardCharsets.UTF_8);
            byte[] dataToSend = new byte[cmdBytes.length + 1];
            dataToSend[0] = 0;
            System.arraycopy(cmdBytes, 0, dataToSend, 1, cmdBytes.length);

            serverService.enqueueSend(dataToSend);
        }
    }



    public synchronized void enqueueMessage(String msg) {
        this.printQueue.offer(msg);
    }
}
