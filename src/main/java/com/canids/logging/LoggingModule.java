package com.canids.logging;

import com.canids.model.CANMessage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * SRS 3.1 & 3.4 Logging Module:
 * Logs normal CAN communications and masquerade intrusion attack events to CSV files.
 */
public class LoggingModule {
    private static final String LOG_DIR = "logs";
    private static final String NORMAL_LOG_FILE = LOG_DIR + "/normal_logs.csv";
    private static final String ATTACK_LOG_FILE = LOG_DIR + "/attack_logs.csv";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public LoggingModule() {
        initLogFiles();
    }

    private void initLogFiles() {
        File dir = new File(LOG_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File normalFile = new File(NORMAL_LOG_FILE);
        if (!normalFile.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(normalFile, true))) {
                writer.println("Timestamp,MessageID,SourceECU,ActualConnection,DestinationECU,Data,Status,Reason");
            } catch (IOException e) {
                System.err.println("Failed to initialize normal_logs.csv: " + e.getMessage());
            }
        }

        File attackFile = new File(ATTACK_LOG_FILE);
        if (!attackFile.exists()) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(attackFile, true))) {
                writer.println("Timestamp,MessageID,ClaimedSourceECU,ActualConnection,DestinationECU,Data,AttackStatus,Reason,ActionTaken");
            } catch (IOException e) {
                System.err.println("Failed to initialize attack_logs.csv: " + e.getMessage());
            }
        }
    }

    public synchronized void logNormalMessage(String actualConnection, CANMessage message, String status, String reason) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        try (PrintWriter writer = new PrintWriter(new FileWriter(NORMAL_LOG_FILE, true))) {
            writer.printf("%s,%d,%s,%s,%s,\"%s\",%s,\"%s\"%n",
                    timestamp,
                    message.getMessageId(),
                    message.getSourceEcu(),
                    actualConnection,
                    message.getDestinationEcu(),
                    message.getData(),
                    status,
                    reason);
        } catch (IOException e) {
            System.err.println("Error writing to normal log: " + e.getMessage());
        }
    }

    public synchronized void logAttackEvent(String actualConnection, CANMessage message, String status, String reason, String actionTaken) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        try (PrintWriter writer = new PrintWriter(new FileWriter(ATTACK_LOG_FILE, true))) {
            writer.printf("%s,%d,%s,%s,%s,\"%s\",%s,\"%s\",%s%n",
                    timestamp,
                    message.getMessageId(),
                    message.getSourceEcu(),
                    actualConnection,
                    message.getDestinationEcu(),
                    message.getData(),
                    status,
                    reason,
                    actionTaken);
        } catch (IOException e) {
            System.err.println("Error writing to attack log: " + e.getMessage());
        }
    }

    public String getNormalLogPath() {
        return NORMAL_LOG_FILE;
    }

    public String getAttackLogPath() {
        return ATTACK_LOG_FILE;
    }
}
