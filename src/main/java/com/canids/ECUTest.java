package com.canids;
import com.canids.ecu.ECUSimulator;
public class ECUTest {
    public static void main(String[] args) {
        ECUSimulator ecu = new ECUSimulator("ECU_ENGINE", "localhost", 5000);
        ecu.start();
    }
}