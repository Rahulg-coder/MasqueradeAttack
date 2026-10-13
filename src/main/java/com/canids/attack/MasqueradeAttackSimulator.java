package com.canids.attack;

import com.canids.model.CANMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/** Test client that sends predefined attack cases to the TCP simulator gateway. */
public class MasqueradeAttackSimulator {
    private final String host;
    private final int port;

    public MasqueradeAttackSimulator(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Attack Type 1: Impersonation / Weak Attacker
     * An unauthorized device connects as UNKNOWN_DEVICE and claims to be ECU_ENGINE.
     */
    public void runImpersonationAttack() {
        System.out.println("\n[ATTACK SIMULATION] Launching Impersonation / Weak Attacker Attack...");
        try (
                Socket socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println("CONNECT|UNKNOWN_ATTACKER_DEVICE");
            System.out.println("  Handshake response: " + reader.readLine());

            CANMessage forgedMessage = new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=60");
            writer.println(forgedMessage.serialize());
            System.out.println("  Sent forged message claiming ECU_ENGINE: " + forgedMessage.serialize());

            String response = reader.readLine();
            System.out.println("  Gateway response: " + response);
        } catch (Exception e) {
            System.err.println("  Attack execution error: " + e.getMessage());
        }
    }

    /**
     * Attack Type 2: Correlated Signal / Statistical Anomaly Attack
     * An attacker sends forged speed values exceeding legitimate physical bounds (Z-score anomaly).
     */
    public void runStatisticalSignalAttack() {
        System.out.println("\n[ATTACK SIMULATION] Launching Correlated Signal / Extreme Value Attack...");
        try (
                Socket socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println("CONNECT|ECU_ENGINE");
            System.out.println("  Handshake response: " + reader.readLine());

            CANMessage extremeMessage = new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=220");
            writer.println(extremeMessage.serialize());
            System.out.println("  Sent abnormal payload (SPEED=220): " + extremeMessage.serialize());

            String response = reader.readLine();
            System.out.println("  Gateway response: " + response);
        } catch (Exception e) {
            System.err.println("  Attack execution error: " + e.getMessage());
        }
    }

    /**
     * Attack Type 3: Out-of-Sequence MSG Transition Attack
     * An attacker attempts an invalid message sequence transition not in the learned JGraphT MSG graph.
     */
    public void runSequenceTransitionAttack() {
        System.out.println("\n[ATTACK SIMULATION] Launching MSG Sequence Transition Anomaly Attack...");
        try (
                Socket socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println("CONNECT|ECU_SENSOR");
            System.out.println("  Handshake response: " + reader.readLine());

            // Exercise the simulator rule for an unlearned message pattern and destination.
            CANMessage sequenceMessage = new CANMessage(999, "ECU_SENSOR", "ECU_BRAKE", "UNAUTHORIZED_CMD=1");
            writer.println(sequenceMessage.serialize());
            System.out.println("  Sent invalid sequence pattern: " + sequenceMessage.serialize());

            String response = reader.readLine();
            System.out.println("  Gateway response: " + response);
        } catch (Exception e) {
            System.err.println("  Attack execution error: " + e.getMessage());
        }
    }
}
