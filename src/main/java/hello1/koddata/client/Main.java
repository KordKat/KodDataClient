package hello1.koddata.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Map<String, String> argMap = new HashMap<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String key = arg.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    argMap.put(key, args[i + 1]);
                    i++;
                } else {
                    System.err.println("Missing value for argument: " + arg);
                    printUsageAndExit();
                }
            } else {
                System.err.println("Unexpected argument: " + arg);
                printUsageAndExit();
            }
        }

        // Required params
        String host = argMap.get("host");
        String portStr = argMap.get("port");
        String username = argMap.get("username");

        if (host == null || portStr == null || username == null) {
            System.err.println("Missing required argument(s).");
            printUsageAndExit();
        }

        String password = argMap.getOrDefault("pass", "");
        String sessionIdStr = argMap.getOrDefault("sessionId", "0");

        int port;
        long sessionId;

        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            System.err.println("Port must be a valid integer.");
            printUsageAndExit();
            return; // unreachable but added to keep compiler happy
        }

        try {
            sessionId = Long.parseLong(sessionIdStr);
        } catch (NumberFormatException e) {
            System.err.println("SessionId must be a valid long integer.");
            printUsageAndExit();
            return;
        }

        try {
            start(host, port, username, password, sessionId);
        } catch (IOException e) {
            System.err.println("Failed to start services: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage:");
        System.err.println("  java -jar KodDataClient.jar --host <host> --port <port> --username <username> [--pass <password>] [--sessionId <sessionId>]");
        System.exit(1);
    }

    private static void start(String host, int port, String username, String password, long sessionId) throws IOException {
        TerminalService terminalService = new TerminalService("kdsh> ");
        ServerService serverService = new ServerService(host, port, username, password, sessionId);
        terminalService.initServerService(serverService);
        serverService.initTerminalService(terminalService);

        byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
        byte[] passwordBytes = password.getBytes(StandardCharsets.UTF_8);
        int cap = usernameBytes.length + passwordBytes.length + 8 + 1 + 8;
        ByteBuffer buffer = ByteBuffer.allocate(cap);

        serverService.start();

        buffer.put((byte) 2); // login mode
        buffer.putInt(usernameBytes.length);
        buffer.put(usernameBytes);
        buffer.putInt(passwordBytes.length);
        buffer.put(passwordBytes);
        buffer.putLong(sessionId);

        byte[] array = new byte[cap];
        buffer.flip();
        buffer.get(array);
        serverService.enqueueSend(array);

        boolean loginResult = serverService.waitForLogin();
        if (!loginResult) {
            System.out.println("Login failed: Invalid credentials or session.");
            return;
        }

        terminalService.start();

        terminalService.enqueueMessage("=====================================");
        terminalService.enqueueMessage("Welcome to KodData Shell v1.0!");
        terminalService.enqueueMessage("User: " + username);
        terminalService.enqueueMessage("Connected to server at " + host + ":" + port);
        terminalService.enqueueMessage("=====================================");
    }



}
