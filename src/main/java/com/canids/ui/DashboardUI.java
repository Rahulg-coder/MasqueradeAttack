package com.canids.ui;

import com.canids.attack.MasqueradeAttackSimulator;
import com.canids.ecu.ECUSimulator;
import com.canids.server.CANGatewayServer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SRS 2.2 #10 & SRS 3.3 Dashboard Module:
 * Clean JavaFX Graphical User Interface for real-time CAN Intrusion Detection monitoring,
 * external network attack inspection, JGraphT topology visualization summary, and simulation controls.
 */
public class DashboardUI extends Application {

    private CANGatewayServer gatewayServer;
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<String> alertEntries = FXCollections.observableArrayList();

    private Label statusLabel;
    private Label ipInfoLabel;
    private Label messageCountLabel;
    private Label alertCountLabel;
    private TextArea topologyTextArea;
    private Button startServerBtn;
    private Button stopServerBtn;

    private int totalMessages = 0;
    private int totalAlerts = 0;
    private final List<ECUSimulator> activeEcuSimulators = new ArrayList<>();

    public static class LogEntry {
        private final SimpleStringProperty timestamp;
        private final SimpleStringProperty claimedSource;
        private final SimpleStringProperty actualConnection;
        private final SimpleStringProperty destination;
        private final SimpleStringProperty msgId;
        private final SimpleStringProperty payload;
        private final SimpleStringProperty status;
        private final SimpleStringProperty reason;

        public LogEntry(String timestamp, String claimedSource, String actualConnection,
                        String destination, String msgId, String payload, String status, String reason) {
            this.timestamp = new SimpleStringProperty(timestamp);
            this.claimedSource = new SimpleStringProperty(claimedSource);
            this.actualConnection = new SimpleStringProperty(actualConnection);
            this.destination = new SimpleStringProperty(destination);
            this.msgId = new SimpleStringProperty(msgId);
            this.payload = new SimpleStringProperty(payload);
            this.status = new SimpleStringProperty(status);
            this.reason = new SimpleStringProperty(reason);
        }

        public String getTimestamp() { return timestamp.get(); }
        public String getClaimedSource() { return claimedSource.get(); }
        public String getActualConnection() { return actualConnection.get(); }
        public String getDestination() { return destination.get(); }
        public String getMsgId() { return msgId.get(); }
        public String getPayload() { return payload.get(); }
        public String getStatus() { return status.get(); }
        public String getReason() { return reason.get(); }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("CAN Masquerade Intrusion Detection System Console");

        HBox headerBox = createHeaderBox();

        HBox controlBox = createControlBox();

        TableView<LogEntry> messageTable = createMessageTable();

        VBox rightSidePanel = createRightSidePanel();

        BorderPane rootPane = new BorderPane();
        VBox topContainer = new VBox(headerBox, controlBox);
        rootPane.setTop(topContainer);
        rootPane.setCenter(messageTable);
        rootPane.setRight(rightSidePanel);
        BorderPane.setMargin(messageTable, new Insets(10));
        BorderPane.setMargin(rightSidePanel, new Insets(10, 10, 10, 0));

        Scene scene = new Scene(rootPane, 1240, 750);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> stopAllServices());
        primaryStage.show();

        // Start the gateway alongside the dashboard.
        startGatewayServer();
    }

    private HBox createHeaderBox() {
        HBox header = new HBox(20);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setStyle("-fx-background-color: #1e293b;");
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("CAN IDS Security Console");
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        titleLabel.setTextFill(Color.WHITE);

        statusLabel = new Label("Server: RUNNING");
        statusLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        statusLabel.setTextFill(Color.LIME);

        String localIp = CANGatewayServer.getLocalIpAddress();
        ipInfoLabel = new Label("Network IP: " + localIp + ":5000");
        ipInfoLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        ipInfoLabel.setTextFill(Color.YELLOW);

        messageCountLabel = new Label("Processed: 0");
        messageCountLabel.setFont(Font.font("Segoe UI", 14));
        messageCountLabel.setTextFill(Color.LIGHTBLUE);

        alertCountLabel = new Label("Alerts: 0");
        alertCountLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        alertCountLabel.setTextFill(Color.PINK);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(titleLabel, spacer, statusLabel, ipInfoLabel, messageCountLabel, alertCountLabel);
        return header;
    }

    private HBox createControlBox() {
        HBox controls = new HBox(12);
        controls.setPadding(new Insets(10, 20, 10, 20));
        controls.setStyle("-fx-background-color: #334155;");
        controls.setAlignment(Pos.CENTER_LEFT);

        startServerBtn = new Button("Start Gateway");
        startServerBtn.setStyle("-fx-background-color: #22c55e; -fx-text-fill: white; -fx-font-weight: bold;");
        startServerBtn.setOnAction(e -> startGatewayServer());

        stopServerBtn = new Button("Stop Gateway");
        stopServerBtn.setStyle("-fx-background-color: #ef4444; -fx-text-fill: white; -fx-font-weight: bold;");
        stopServerBtn.setOnAction(e -> stopGatewayServer());

        Button runTrafficBtn = new Button("Simulate Normal Traffic");
        runTrafficBtn.setStyle("-fx-background-color: #3b82f6; -fx-text-fill: white; -fx-font-weight: bold;");
        runTrafficBtn.setOnAction(e -> simulateNormalTraffic());

        Button runAttackBtn = new Button("Simulate Local Attack");
        runAttackBtn.setStyle("-fx-background-color: #dc2626; -fx-text-fill: white; -fx-font-weight: bold;");
        runAttackBtn.setOnAction(e -> runLocalAttack());

        Button clearLogsBtn = new Button("Clear Display");
        clearLogsBtn.setStyle("-fx-background-color: #64748b; -fx-text-fill: white;");
        clearLogsBtn.setOnAction(e -> {
            logEntries.clear();
            alertEntries.clear();
        });

        Label externalHint = new Label("ℹ️ External devices can connect to IP " + CANGatewayServer.getLocalIpAddress() + " Port 5000");
        externalHint.setTextFill(Color.LIGHTYELLOW);

        controls.getChildren().addAll(startServerBtn, stopServerBtn, runTrafficBtn, runAttackBtn, clearLogsBtn, externalHint);
        return controls;
    }

    @SuppressWarnings("unchecked")
    private TableView<LogEntry> createMessageTable() {
        TableView<LogEntry> table = new TableView<>(logEntries);

        TableColumn<LogEntry, String> timeCol = new TableColumn<>("Timestamp");
        timeCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getTimestamp()));
        timeCol.setPrefWidth(100);

        TableColumn<LogEntry, String> claimedCol = new TableColumn<>("Claimed ECU");
        claimedCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getClaimedSource()));
        claimedCol.setPrefWidth(110);

        TableColumn<LogEntry, String> actualCol = new TableColumn<>("Actual Connection");
        actualCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getActualConnection()));
        actualCol.setPrefWidth(150);

        TableColumn<LogEntry, String> destCol = new TableColumn<>("Destination");
        destCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDestination()));
        destCol.setPrefWidth(110);

        TableColumn<LogEntry, String> idCol = new TableColumn<>("Msg ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getMsgId()));
        idCol.setPrefWidth(60);

        TableColumn<LogEntry, String> dataCol = new TableColumn<>("Payload Data");
        dataCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPayload()));
        dataCol.setPrefWidth(120);

        TableColumn<LogEntry, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus()));
        statusCol.setPrefWidth(140);
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.contains("ATTACK") || item.contains("ABNORMAL") || item.contains("ANOMALY") || item.contains("BLOCKED")) {
                        setStyle("-fx-background-color: #fca5a5; -fx-text-fill: #991b1b; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-background-color: #bbf7d0; -fx-text-fill: #166534;");
                    }
                }
            }
        });

        TableColumn<LogEntry, String> reasonCol = new TableColumn<>("Reason / Mitigation Details");
        reasonCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getReason()));
        reasonCol.setPrefWidth(240);

        table.getColumns().addAll(timeCol, claimedCol, actualCol, destCol, idCol, dataCol, statusCol, reasonCol);
        table.setPlaceholder(new Label("No CAN messages received yet. Click 'Simulate Normal Traffic' or send from external laptop."));
        return table;
    }

    private VBox createRightSidePanel() {
        VBox panel = new VBox(12);
        panel.setPrefWidth(350);

        Label topologyHeader = new Label("JGraphT MSG & Signal Statistics");
        topologyHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));

        topologyTextArea = new TextArea();
        topologyTextArea.setEditable(false);
        topologyTextArea.setPrefHeight(250);
        topologyTextArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        Label alertHeader = new Label("Intrusion Alerts & Blocked Devices");
        alertHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        alertHeader.setTextFill(Color.DARKRED);

        ListView<String> alertListView = new ListView<>(alertEntries);
        alertListView.setPrefHeight(280);

        panel.getChildren().addAll(topologyHeader, topologyTextArea, alertHeader, alertListView);
        return panel;
    }

    private synchronized void startGatewayServer() {
        if (gatewayServer != null && gatewayServer.isRunning()) return;
        gatewayServer = new CANGatewayServer(5000);

        gatewayServer.getIds().addListener((actualClientId, message, result) -> {
            Platform.runLater(() -> {
                totalMessages++;
                messageCountLabel.setText("Processed: " + totalMessages);

                if (result.blocked()) {
                    totalAlerts++;
                    alertCountLabel.setText("Alerts: " + totalAlerts);
                    alertEntries.add(0, String.format("[%s] %s: %s",
                            LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                            result.status(), result.reason()));
                }

                logEntries.add(0, new LogEntry(
                        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")),
                        message.getSourceEcu(),
                        actualClientId,
                        message.getDestinationEcu(),
                        String.valueOf(message.getMessageId()),
                        message.getData(),
                        result.status(),
                        result.reason()
                ));

                updateTopologyDisplay();
            });
        });

        gatewayServer.start();
        statusLabel.setText("Server: RUNNING");
        statusLabel.setTextFill(Color.LIME);
        updateTopologyDisplay();
    }

    private synchronized void stopGatewayServer() {
        stopAllServices();
        statusLabel.setText("Server: STOPPED");
        statusLabel.setTextFill(Color.ORANGE);
    }

    private void simulateNormalTraffic() {
        if (gatewayServer == null || !gatewayServer.isRunning()) {
            startGatewayServer();
        }
        String[] ecus = {"ECU_ENGINE", "ECU_BRAKE", "ECU_SENSOR"};
        for (String ecuId : ecus) {
            ECUSimulator simulator = new ECUSimulator(ecuId, "localhost", 5000);
            activeEcuSimulators.add(simulator);
            new Thread(simulator).start();
        }
    }

    private void runLocalAttack() {
        if (gatewayServer == null || !gatewayServer.isRunning()) {
            startGatewayServer();
        }
        new Thread(() -> {
            MasqueradeAttackSimulator simulator = new MasqueradeAttackSimulator("localhost", 5000);
            simulator.runImpersonationAttack();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            simulator.runStatisticalSignalAttack();
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            simulator.runSequenceTransitionAttack();
            Platform.runLater(this::updateTopologyDisplay);
        }).start();
    }

    private void updateTopologyDisplay() {
        if (gatewayServer != null && gatewayServer.getIds() != null) {
            String summary = gatewayServer.getIds().graphSummary();
            topologyTextArea.setText(summary + "\n\nAllowed Edge Sequence Baseline established.\nStatistical Z-Score threshold = 3.0\nBlocked Clients/IPs: " +
                    gatewayServer.getIds().getMitigationModule().getBlockedClientIds() + " " +
                    gatewayServer.getIds().getMitigationModule().getBlockedIpAddresses());
        }
    }

    private void stopAllServices() {
        for (ECUSimulator sim : activeEcuSimulators) {
            sim.stop();
        }
        activeEcuSimulators.clear();
        if (gatewayServer != null) {
            gatewayServer.stop();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
