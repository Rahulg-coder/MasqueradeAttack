package com.canids.ecu;
import com.canids.model.CANMessage;
import java.io.PrintWriter;
import java.net.Socket;
public class ECUSimulator {
    private final String ecuId;
    private final String host;
    private final int port;
    public ECUSimulator(String ecuId, String host, int port) {
        this.ecuId = ecuId;
        this.host = host;
        this.port = port;
    }
    public void start() {
        try {
            Socket socket = new Socket(host, port);
            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            System.out.println(ecuId + " connected to gateway.");
            CANMessage message = new CANMessage(100, ecuId, "ECU_DASHBOARD", "SPEED=60");
            writer.println(message.serialize());
            System.out.println("Message sent: " + message);
            socket.close();
        } catch (Exception e) {
            System.out.println("ECU connection failed: " + e.getMessage());
        }
    }
}