package com.canids.attack;

import com.canids.model.CANMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * SRS 1.2 & 2.4 External Attack Client:
 * Runnable script that can be executed on another laptop or remote network device to send
 * external masquerade attack injections to the CAN Gateway over TCP/IP.
 */
public class ExternalAttackClient {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("==========================================================================");
        System.out.println("   EXTERNAL LAPTOP / REMOTE CAN MASQUERADE ATTACK CLIENT");
        System.out.println("==========================================================================");
        System.out.print("Enter CAN Gateway IP Address (e.g. 192.168.x.x or localhost): ");
        String host = scanner.nextLine().trim();
        if (host.isEmpty()) host = "localhost";

        System.out.print("Enter CAN Gateway Port [default 5000]: ");
        String portStr = scanner.nextLine().trim();
        int port = portStr.isEmpty() ? 5000 : Integer.parseInt(portStr);

        System.out.println("\nConnecting to CAN Gateway at " + host + ":" + port + "...");

        try (
                Socket socket = new Socket(host, port);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("[Connected!] Enter Client Device Name (e.g. EXTERNAL_LAPTOP): ");
            String clientId = scanner.nextLine().trim();
            if (clientId.isEmpty()) clientId = "EXTERNAL_LAPTOP_ATTACKER";

            writer.println("CONNECT|" + clientId);
            String greeting = reader.readLine();
            System.out.println("Gateway Handshake Response: " + greeting);

            if (greeting != null && greeting.contains("BLOCKED")) {
                System.out.println("\n❌ Your device IP has been BLOCKED by the CAN Gateway Mitigation Module!");
                return;
            }

            System.out.println("\nSelect Attack Vector to launch from this device:");
            System.out.println("1. Header Impersonation Attack (Claims to be ECU_ENGINE)");
            System.out.println("2. Extreme Signal Anomaly Attack (SPEED=250 payload)");
            System.out.println("3. Invalid MSG Sequence Attack (MSG_ID=999 to ECU_BRAKE)");
            System.out.print("Choice (1-3): ");

            String attackChoice = scanner.nextLine().trim();
            CANMessage msg;
            switch (attackChoice) {
                case "2":
                    msg = new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=250.0");
                    break;
                case "3":
                    msg = new CANMessage(999, "ECU_SENSOR", "ECU_BRAKE", "UNAUTHORIZED_CMD=1");
                    break;
                case "1":
                default:
                    msg = new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=60.0");
                    break;
            }

            System.out.println("\nSending Forged CAN Message: " + msg.serialize());
            writer.println(msg.serialize());

            String response = reader.readLine();
            System.out.println("Gateway Inspection Result: " + response);

            if (response != null && (response.contains("ATTACK") || response.contains("ABNORMAL") || response.contains("ANOMALY") || response.contains("BLOCKED"))) {
                String blockMsg = reader.readLine();
                System.out.println("Gateway Action: " + blockMsg);
                System.out.println("\n🛡️ ATTACK DETECTED & PREVENTED! Your device IP/ID has been blocked by the gateway.");
                System.out.println("Any subsequent connection attempt from this laptop will be rejected instantly.");
            }
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }
}
