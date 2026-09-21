package com.canids;
import com.canids.model.CANMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
public class ECUTest {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("CAN ECU Test");
        System.out.println("1. Legitimate driving ECU");
        System.out.println("2. Attacker / unknown device");
        System.out.print("Choose mode: ");
        String mode = scanner.nextLine().trim();

        boolean attackerMode = "2".equals(mode);
        String actualClientId = attackerMode ? "UNKNOWN_DEVICE" : "ECU_ENGINE";

        try (
                Socket socket = new Socket("localhost", 5000);
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println("CONNECT|" + actualClientId);
            System.out.println(reader.readLine());
            printControls(attackerMode);

            while (true) {
                System.out.print("command> ");
                String command = scanner.nextLine().trim().toLowerCase();
                if ("q".equals(command)) {
                    break;
                }

                CANMessage message = attackerMode
                        ? attackerMessage(command)
                        : legitimateDrivingMessage(command);

                if (message == null) {
                    System.out.println("Unknown command. Use w, a, s, d, x, or q.");
                    continue;
                }

                writer.println(message.serialize());
                System.out.println("Sent: " + message.serialize());

                String response = reader.readLine();
                if (response == null) {
                    System.out.println("Gateway closed the connection.");
                    break;
                }

                System.out.println("Gateway: " + response);
                if (response.contains("ATTACK") || response.contains("ABNORMAL") || response.contains("SUSPICIOUS")) {
                    String blockMessage = reader.readLine();
                    if (blockMessage != null) {
                        System.out.println("Gateway: " + blockMessage);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("ECU test failed: " + e.getMessage());
        }
    }

    private static void printControls(boolean attackerMode) {
        System.out.println();
        System.out.println(attackerMode ? "Attacker mode: forged commands will be blocked." : "Driving mode: WASD sends allowed vehicle messages.");
        System.out.println("w = accelerate, s = brake, a = steer left, d = steer right");
        System.out.println("x = unwanted forged brake-off command, q = quit");
    }

    private static CANMessage legitimateDrivingMessage(String command) {
        return switch (command) {
            case "w" -> new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=60");
            case "s" -> new CANMessage(400, "ECU_ENGINE", "ECU_BRAKE", "BRAKE_REQUEST=ON");
            case "a" -> new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "STEER=LEFT");
            case "d" -> new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "STEER=RIGHT");
            case "x" -> new CANMessage(200, "ECU_ENGINE", "ECU_BRAKE", "BRAKE=OFF");
            default -> null;
        };
    }

    private static CANMessage attackerMessage(String command) {
        return switch (command) {
            case "w" -> new CANMessage(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED=200");
            case "s", "x" -> new CANMessage(200, "ECU_ENGINE", "ECU_BRAKE", "BRAKE=OFF");
            case "a" -> new CANMessage(300, "ECU_SENSOR", "ECU_DASHBOARD", "STEER=LEFT");
            case "d" -> new CANMessage(300, "ECU_SENSOR", "ECU_DASHBOARD", "STEER=RIGHT");
            default -> null;
        };
    }
}
