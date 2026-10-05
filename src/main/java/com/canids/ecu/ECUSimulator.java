package com.canids.ecu;

import com.canids.model.CANMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

/**
 * SRS 3.1 ECU Simulation:
 * Simulates legitimate vehicle ECUs (Engine, Brake, Sensor, Dashboard) exchanging periodic CAN traffic.
 */
public class ECUSimulator implements Runnable {
    private final String ecuId;
    private final String host;
    private final int port;
    private volatile boolean running = true;
    private final Random random = new Random();

    public ECUSimulator(String ecuId, String host, int port) {
        this.ecuId = ecuId;
        this.host = host;
        this.port = port;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        try (
                Socket socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("[ECU Simulator] " + ecuId + " connecting to gateway...");
            writer.println("CONNECT|" + ecuId);
            String greeting = reader.readLine();
            System.out.println("[ECU Simulator] " + ecuId + " handshake response: " + greeting);

            while (running && !socket.isClosed()) {
                CANMessage msg = generateLegitimateMessage();
                if (msg != null) {
                    writer.println(msg.serialize());
                    String resp = reader.readLine();
                    if (resp == null || resp.contains("BLOCKED")) {
                        System.out.println("[ECU Simulator] " + ecuId + " connection blocked or terminated by gateway.");
                        break;
                    }
                }
                Thread.sleep(1000 + random.nextInt(500));
            }
        } catch (Exception e) {
            System.err.println("[ECU Simulator] " + ecuId + " error: " + e.getMessage());
        }
    }

    private CANMessage generateLegitimateMessage() {
        switch (ecuId) {
            case "ECU_ENGINE":
                double speed = 55.0 + random.nextDouble() * 10.0;
                return new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", String.format("SPEED=%.1f", speed));
            case "ECU_BRAKE":
                return new CANMessage(200, "ECU_BRAKE", "ECU_DASHBOARD", "BRAKE_STATUS=0.0");
            case "ECU_SENSOR":
                double temp = 88.0 + random.nextDouble() * 5.0;
                return new CANMessage(300, "ECU_SENSOR", "ECU_DASHBOARD", String.format("TEMP=%.1f", temp));
            default:
                return new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=60.0");
        }
    }
}
