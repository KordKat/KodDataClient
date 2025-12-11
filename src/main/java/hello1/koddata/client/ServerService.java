package hello1.koddata.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class ServerService {

    private TerminalService terminalService;

    private FileState fileState = null;

    private String host, username, password;
    private int port;
    private long sessionId;

    private Queue<byte[]> sendQueue;

    private Thread sendThread;
    private Thread readThread;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    private volatile boolean waitingForLogin = false;
    private CountDownLatch loginLatch;
    private volatile Boolean loginSuccess = null; // null means no response yet

    private volatile boolean running = false;

    public ServerService(String host, int port, String username, String password, long sessionId) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.sessionId = sessionId;

        this.sendQueue = new ConcurrentLinkedQueue<>();

        this.readThread = new Thread(this::doRead);
        this.sendThread = new Thread(this::doSend);
    }

    public void start() throws IOException {
        this.running = true;
        this.socket = new Socket(this.host, this.port);
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.readThread.start();
        this.sendThread.start();
    }

    public void stop() {
        running = false;

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (readThread != null && readThread.isAlive()) {
            readThread.interrupt();
        }
        if (sendThread != null && sendThread.isAlive()) {
            sendThread.interrupt();
        }
    }

    public void initTerminalService(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    public boolean waitForLogin(byte[] array) {
        if (waitingForLogin) return false;
        enqueueSend(array);
        waitingForLogin = true;
        loginLatch = new CountDownLatch(1);
        loginSuccess = null;

        try {
            loginLatch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            waitingForLogin = false;
        }

        return Boolean.TRUE.equals(loginSuccess);
    }

    private void doRead() {
        byte[] buffer = new byte[1000000];

        try {
            while (running) {
                int readBytes = inputStream.read(buffer);
                if (readBytes == -1) break;

                if (fileState != null) {
                    // append only the valid readBytes
                    fileState.receivedBuffer.put(buffer, 0, readBytes);
                    fileState.receivedBytes += readBytes;

                    // finished?
                    if (!fileState.receivedBuffer.hasRemaining()) {
                        fileState.doSave();
                        terminalService.enqueueMessage("File saved: " + fileState.name);
                        fileState = null;
                    }

                    continue;
                }

                // normal packet handling
                if (readBytes > 0) {
                    byte[] data = new byte[readBytes];
                    System.arraycopy(buffer, 0, data, 0, readBytes);
                    // login response
                    if (waitingForLogin) {
                        byte loginByte = data[0];
                        loginSuccess = loginByte != 2;
                        ByteBuffer byteBuf = ByteBuffer.wrap(data);
                        byteBuf.get();
                        sessionId = byteBuf.getInt();
                        loginLatch.countDown();
                    }

                    // file header starts with 'K' 'D'
                    if (data[0] == 'K' && data[1] == 'D') {
                        ByteBuffer buf = ByteBuffer.wrap(data);
                        buf.get(); buf.get(); // skip 'K','D'

                        int nameSize = buf.getInt();
                        byte[] nameBytes = new byte[nameSize];
                        buf.get(nameBytes);

                        int dataSize = buf.getInt();   // FULL file size

                        // first chunk of file
                        byte[] firstChunk = new byte[buf.remaining()];
                        buf.get(firstChunk);

                        // initialize receiver
                        fileState = new FileState();
                        fileState.name = new String(nameBytes, StandardCharsets.UTF_8);
                        fileState.expectedBytes = dataSize;
                        fileState.receivedBuffer = ByteBuffer.allocate(dataSize);

                        // store first chunk
                        fileState.receivedBuffer.put(firstChunk);
                        fileState.receivedBytes = firstChunk.length;

                        // immediately done?
                        if (!fileState.receivedBuffer.hasRemaining()) {
                            fileState.doSave();
                            terminalService.enqueueMessage("File saved immediately: " + fileState.name);
                            fileState = null;
                        }

                        continue;
                    }

                    // fallback normal text message
                    if (terminalService != null) {
                        terminalService.enqueueMessage(new String(data, StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException e) {
            if (running) e.printStackTrace();
        } finally {
            stop();
        }
    }


    private void doSend() {
        try {
            while (running) {
                byte[] buffer = sendQueue.poll();

                if (buffer != null) {
                    outputStream.write(buffer);
                    outputStream.flush();
                } else {
                    // Avoid busy wait
                    Thread.sleep(50);
                }
            }
        } catch (IOException | InterruptedException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            stop();
        }
    }

    public long getSessionId() {
        return sessionId;
    }

    public void enqueueSend(byte[] buffer) {
        this.sendQueue.offer(buffer);
    }
}
