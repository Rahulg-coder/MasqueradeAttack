package com.canids.server;
import com.canids.model.CANMessage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            String message;
            while ((message = reader.readLine()) != null) {
                if (message.startsWith("CONNECT|")) {
                    actualClientId = message.substring("CONNECT|".length()).trim();
                    writer.println("CONNECTED|" + actualClientId);
                    System.out.println("Connection registered as: " + actualClientId);
                    continue;
                }

                CANMessage canMessage = CANMessage.parse(message);
                CANIntrusionDetectionSystem.DetectionResult result = ids.inspect(actualClientId, canMessage);
                printMessageReport(actualClientId, canMessage, result);
                writer.println(result.status() + "|" + result.reason());

                if (result.blocked()) {
                    writer.println("CONNECTION BLOCKED");
                    break;
                }
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid CAN message blocked: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {

            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void printMessageReport(String actualClientId, CANMessage message,
                                    CANIntrusionDetectionSystem.DetectionResult result) {
        System.out.println();
        System.out.println("New Message Received");
        System.out.println("Claimed Source ECU : " + message.getSourceEcu());
        System.out.println("Actual Connection  : " + actualClientId);
        System.out.println("Destination ECU    : " + message.getDestinationEcu());
        System.out.println("Message ID         : " + message.getMessageId());
        System.out.println("Message Data       : " + message.getData());
        System.out.println("Time               : " + message.getTimestamp());
        System.out.println("Result             : " + result.status());
        System.out.println("Reason             : " + result.reason());
        System.out.println("Action             : " + (result.blocked() ? "CONNECTION BLOCKED" : "MESSAGE ACCEPTED"));
        System.out.println(ids.graphSummary());
    }
}
