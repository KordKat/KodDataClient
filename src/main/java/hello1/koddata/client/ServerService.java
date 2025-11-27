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

    private FileState fileState;

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

    public boolean waitForLogin() {
        if (waitingForLogin) return false;

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
        byte[] buffer = new byte[8192];
        try {
            while (running) {
                int readBytes = inputStream.read(buffer);
                if (readBytes == -1) {
                    break;
                }

                if(fileState != null){
                    fileState.receivedBuffer.put(buffer);
                    if(!fileState.receivedBuffer.hasRemaining()){
                        fileState.doSave();
                    }
                }else if (readBytes > 0) {
                    byte[] data = new byte[readBytes];
                    System.arraycopy(buffer, 0, data, 0, readBytes);

                    if (waitingForLogin && data.length > 0) {
                        byte loginByte = data[0];
                        if (loginByte == 1) {
                            loginSuccess = true;
                            loginLatch.countDown();
                        } else if (loginByte == 2) {
                            loginSuccess = false;
                            loginLatch.countDown();
                        }
                    }

                    if(data[0] == 'K' && data[1] == 'D'){
                        //downloading file
                        ByteBuffer buf = ByteBuffer.wrap(data);
                        buf.get(); buf.get();

                        int nameSize = buf.getInt();
                        byte[] nameBytes = new byte[nameSize];
                        buf.get(nameBytes);

                        int dataSize = buf.getInt();

                        byte[] fileData = new byte[dataSize];

                        buf.get(fileData);

                        ByteBuffer rbuf = ByteBuffer.allocate(dataSize);
                        rbuf.put(fileData);

                        fileState = new FileState();
                        fileState.name = new String(nameBytes, StandardCharsets.UTF_8);
                        fileState.expectedBytes = dataSize;
                        fileState.receivedBuffer = rbuf;
                        fileState.receivedBytes = rbuf.position();

                        if(!fileState.receivedBuffer.hasRemaining()){
                            fileState.doSave();
                        }

                    }else if (terminalService != null) {
                        terminalService.enqueueMessage(new String(data, StandardCharsets.UTF_8));
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
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

    public void enqueueSend(byte[] buffer) {
        this.sendQueue.offer(buffer);
    }
}
