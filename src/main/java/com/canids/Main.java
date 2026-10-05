package com.canids;

import com.canids.ui.DashboardUI;
import javafx.application.Application;

/**
 * Main Application Launcher for CAN Masquerade Detection System.
 * Directly launches the JavaFX Security Dashboard Console UI.
 */
public class Main {
    public static void main(String[] args) {
        System.out.println("==========================================================================");
        System.out.println("   CAN MASQUERADE INTRUSION DETECTION SYSTEM (SRS & IEEE PAPER IMPL)");
        System.out.println("   Launching JavaFX Security Console Dashboard...");
        System.out.println("==========================================================================");
        Application.launch(DashboardUI.class, args);
    }
}
