package com.canids;
import com.canids.server.CANGatewayServer;
public class Main {
    public static void main(String[] args) {
        CANGatewayServer server = new CANGatewayServer(5000);
        server.start();
    }
}
