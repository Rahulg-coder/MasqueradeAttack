package com.canids.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.NetworkInterface;
import java.net.Inet4Address;
import java.util.Enumeration;

/**
 * SRS 3.1 CAN Gateway Server:
 * Central TCP Server listening on 0.0.0.0 (all network interfaces) so that both local ECUs
 * and external network devices (e.g. another laptop over Wi-Fi/LAN) can connect to port 5000.
 */
public class CANGatewayServer {
    private final int port;
    private final CANIntrusionDetectionSystem ids;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private Thread serverThread;

    public CANGatewayServer(int port) {
        this.port = port;
        this.ids = new CANIntrusionDetectionSystem();
    }

    public CANGatewayServer(int port, CANIntrusionDetectionSystem ids) {
        this.port = port;
        this.ids = ids;
    }

    public synchronized void start() {
        if (running) {
            System.out.println("[Gateway] Server is already running.");
            return;
        }

        running = true;
        serverThread = new Thread(() -> {
            try {
                // Bind to 0.0.0.0 to accept connections from localhost and external devices on LAN
                serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
                String localIp = getLocalIpAddress();
                System.out.println("==========================================================");
                System.out.println("   CAN Gateway Listening on ALL Interfaces (Port " + port + ")");
                System.out.println("   Local Network IP for External Devices: " + localIp);
                System.out.println("   " + ids.graphSummary());
                System.out.println("==========================================================");

                while (running && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        String clientIp = clientSocket.getInetAddress().getHostAddress();

                        // Check early IP-level blocking before handling thread allocation
                        if (ids.getMitigationModule().isBlocked(null, clientIp)) {
                            System.out.println("[Gateway] Rejected incoming connection from BLOCKED IP: " + clientIp);
                            clientSocket.close();
                            continue;
                        }

                        System.out.println("[Gateway] Incoming connection from: " + clientIp);
                        ClientHandler handler = new ClientHandler(clientSocket, ids);
                        new Thread(handler).start();
                    } catch (IOException e) {
                        if (!running) {
                            break;
                        }
                        System.err.println("[Gateway] Accept error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("[Gateway] Server socket error on port " + port + ": " + e.getMessage());
            } finally {
                running = false;
            }
        });
        serverThread.start();
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("[Gateway] Server socket closed.");
            } catch (IOException e) {
                System.err.println("[Gateway] Error closing server socket: " + e.getMessage());
            }
        }
    }

    public static String getLocalIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }

    public boolean isRunning() {
        return running;
    }

    public CANIntrusionDetectionSystem getIds() {
        return ids;
    }

    public int getPort() {
        return port;
    }
}
