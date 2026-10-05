package com.canids.server;

import com.canids.model.CANMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * SRS 3.1 & IEEE Paper External Connection Handler:
 * Listens to local and remote TCP socket client connections (e.g. external laptops),
 * checks IP/client mitigation blacklist, and inspects all CAN messages.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final CANIntrusionDetectionSystem ids;

    public ClientHandler(Socket socket, CANIntrusionDetectionSystem ids) {
        this.socket = socket;
        this.ids = ids;
    }

    @Override
    public void run() {
        String actualClientId = "UNKNOWN_DEVICE";
        String clientIp = (socket.getInetAddress() != null) ? socket.getInetAddress().getHostAddress() : "UNKNOWN_IP";

        try {
            // Check IP blacklist immediately
            if (ids.getMitigationModule().isBlocked(null, clientIp)) {
                System.out.println("[Gateway] Rejecting connection attempt from BLOCKED IP: " + clientIp);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                writer.println("CONNECTION BLOCKED|Your IP address " + clientIp + " has been isolated and blocked.");
                socket.close();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            String message;

            while ((message = reader.readLine()) != null) {
                if (ids.getMitigationModule().isBlocked(actualClientId, clientIp)) {
                    System.out.println("[Gateway] Connection attempt from blocked client/IP rejected: " + actualClientId + " (" + clientIp + ")");
                    writer.println("CONNECTION BLOCKED|Your device has been isolated by the Mitigation Module.");
                    break;
                }

                if (message.startsWith("CONNECT|")) {
                    actualClientId = message.substring("CONNECT|".length()).trim();
                    writer.println("CONNECTED|" + actualClientId);
                    System.out.println("[Gateway] Client registered: ID=" + actualClientId + " IP=" + clientIp);
                    continue;
                }

                try {
                    CANMessage canMessage = CANMessage.parse(message);
                    CANIntrusionDetectionSystem.DetectionResult result = ids.inspect(actualClientId, canMessage, socket);
                    printMessageReport(actualClientId, clientIp, canMessage, result);
                    writer.println(result.status() + "|" + result.reason());

                    if (result.blocked()) {
                        writer.println("CONNECTION BLOCKED");
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    System.out.println("[Gateway] Malformed message received from IP " + clientIp + ": " + message);
                    writer.println("INVALID_FORMAT|Malformed CAN message structure");
                }
            }
        } catch (IOException e) {
            System.out.println("[Gateway] Client " + actualClientId + " (" + clientIp + ") disconnected.");
        } finally {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void printMessageReport(String actualClientId, String clientIp, CANMessage message,
                                     CANIntrusionDetectionSystem.DetectionResult result) {
        System.out.println();
        System.out.println("================== INCOMING CAN MESSAGE ==================");
        System.out.println("Claimed Source ECU : " + message.getSourceEcu());
        System.out.println("Actual Connection  : " + actualClientId + " (IP: " + clientIp + ")");
        System.out.println("Destination ECU    : " + message.getDestinationEcu());
        System.out.println("Message ID         : " + message.getMessageId());
        System.out.println("Message Data       : " + message.getData());
        System.out.println("Timestamp          : " + message.getTimestamp());
        System.out.println("Result Status      : " + result.status());
        System.out.println("Reason             : " + result.reason());
        System.out.println("Action Taken       : " + (result.blocked() ? "CONNECTION BLOCKED & IP ISOLATED" : "MESSAGE ACCEPTED"));
        System.out.println("JGraphT Topology   : " + ids.graphSummary());
        System.out.println("==========================================================");
    }
}
