package com.canids.server;

import com.canids.graph.MessageSequenceGraph;
import com.canids.logging.LoggingModule;
import com.canids.mitigation.MitigationModule;
import com.canids.model.CANMessage;
import com.canids.model.SignalStats;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** Rule-based IDS for the TCP simulator, separate from the ROAD graph-ML pipeline.
 *
 * <p>Flow: reject blocked or unauthenticated clients -> validate claimed source
 * and destination -> optionally learn a normal baseline -> check message patterns
 * and ECU transitions -> compare signal values with learned Z-scores -> accept
 * and log the message or block and log it.
 */
public class CANIntrusionDetectionSystem {
    private final Set<String> registeredEcus = ConcurrentHashMap.newKeySet();
    private final Set<String> allowedPatterns = ConcurrentHashMap.newKeySet();
    private final MessageSequenceGraph msgGraph = new MessageSequenceGraph();
    private final LoggingModule loggingModule = new LoggingModule();
    private final MitigationModule mitigationModule = new MitigationModule();
    private final List<IDSListener> listeners = new CopyOnWriteArrayList<>();

    private boolean learningPhase = true;
    private double zScoreThreshold = 3.0; // Demo IDS threshold; not a ROAD model parameter.

    public CANIntrusionDetectionSystem() {
        registerEcu("ECU_ENGINE");
        registerEcu("ECU_BRAKE");
        registerEcu("ECU_SENSOR");
        registerEcu("ECU_DASHBOARD");

        // Seed normal message patterns and signal samples for the simulator demo.
        learnNormalPattern(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED", 60.0);
        learnNormalPattern(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED", 65.0);
        learnNormalPattern(100, "ECU_ENGINE", "ECU_DASHBOARD", "SPEED", 55.0);
        learnNormalPattern(200, "ECU_BRAKE", "ECU_DASHBOARD", "BRAKE_STATUS", 0.0);
        learnNormalPattern(300, "ECU_SENSOR", "ECU_DASHBOARD", "TEMP", 90.0);
        learnNormalPattern(300, "ECU_SENSOR", "ECU_DASHBOARD", "TEMP", 92.0);
        learnNormalPattern(400, "ECU_ENGINE", "ECU_BRAKE", "BRAKE_REQUEST", 1.0);
    }

    public void addListener(IDSListener listener) {
        listeners.add(listener);
    }

    public void removeListener(IDSListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(String actualClientId, CANMessage message, DetectionResult result) {
        for (IDSListener listener : listeners) {
            try {
                listener.onMessageInspected(actualClientId, message, result);
            } catch (Exception e) {
                System.err.println("[IDS] Listener notification error: " + e.getMessage());
            }
        }
    }

    public synchronized void setLearningPhase(boolean learning) {
        this.learningPhase = learning;
    }

    public boolean isLearningPhase() {
        return learningPhase;
    }

    public synchronized void registerEcu(String ecuId) {
        registeredEcus.add(ecuId);
        msgGraph.addEcuNode(ecuId);
    }

    public synchronized void learnNormalPattern(int messageId, String sourceEcu, String destinationEcu, String signalName, double signalValue) {
        registerEcu(sourceEcu);
        registerEcu(destinationEcu);

        String pattern = patternKey(messageId, sourceEcu, destinationEcu);
        allowedPatterns.add(pattern);

        msgGraph.recordSequenceTransition(sourceEcu, destinationEcu);
        if (signalName != null) {
            msgGraph.recordSignalValue(sourceEcu, signalName, signalValue);
        }
    }

    /** Applies the simulator's IDS rules in order and returns an accept-or-block decision. */
    public synchronized DetectionResult inspect(String actualClientId, CANMessage message, Socket socket) {
        String claimedSource = message.getSourceEcu();
        String destination = message.getDestinationEcu();
        String clientIp = (socket != null && socket.getInetAddress() != null) ? socket.getInetAddress().getHostAddress() : "127.0.0.1";

        DetectionResult result;

        // Step 1: reject connections already on the simulator blocklist.
        if (mitigationModule.isBlocked(actualClientId, clientIp)) {
            result = DetectionResult.blocked("CONNECTION BLOCKED", "Client has been isolated and blocked by Mitigation Module");
            notifyListeners(actualClientId, message, result);
            return result;
        }

        // Step 2: bind the claimed ECU identity to the connected TCP client.
        if (!registeredEcus.contains(actualClientId)) {
            mitigationModule.blockClient(actualClientId, clientIp, socket);
            loggingModule.logAttackEvent(actualClientId, message, "MASQUERADE ATTACK DETECTED",
                    "Unregistered device connection attempting CAN injection: " + actualClientId, "CONNECTION BLOCKED");
            result = DetectionResult.blocked("MASQUERADE ATTACK DETECTED",
                    "Unregistered device connection: " + actualClientId);
            notifyListeners(actualClientId, message, result);
            return result;
        }

        if (!actualClientId.equals(claimedSource)) {
            mitigationModule.blockClient(actualClientId, clientIp, socket);
            loggingModule.logAttackEvent(actualClientId, message, "MASQUERADE ATTACK DETECTED",
                    "Header identity spoofing: Claimed=" + claimedSource + " but Connection=" + actualClientId, "CONNECTION BLOCKED");
            result = DetectionResult.blocked("MASQUERADE ATTACK DETECTED",
                    "Message claims to be from " + claimedSource + " but connection is " + actualClientId);
            notifyListeners(actualClientId, message, result);
            return result;
        }

        if (!registeredEcus.contains(destination)) {
            mitigationModule.blockClient(actualClientId, clientIp, socket);
            loggingModule.logAttackEvent(actualClientId, message, "SUSPICIOUS DESTINATION",
                    "Destination ECU is unregistered: " + destination, "CONNECTION BLOCKED");
            result = DetectionResult.blocked("SUSPICIOUS DESTINATION",
                    "Destination ECU is not registered: " + destination);
            notifyListeners(actualClientId, message, result);
            return result;
        }

        String pattern = patternKey(message.getMessageId(), claimedSource, destination);

        // Learning mode updates the demo's allowed patterns, transitions, and signal baseline.
        if (learningPhase) {
            allowedPatterns.add(pattern);
            msgGraph.recordSequenceTransition(claimedSource, destination);
            extractAndRecordSignals(claimedSource, message.getData());
            loggingModule.logNormalMessage(actualClientId, message, "LEARNED", "Added to baseline graph");
            result = DetectionResult.valid("LEARNED", "Message recorded into normal baseline");
            notifyListeners(actualClientId, message, result);
            return result;
        }

        // Step 3: check the learned message pattern and ECU transition rules.
        String lastObserved = msgGraph.getLastObservedEcu();
        if (lastObserved != null && !msgGraph.hasValidTransition(lastObserved, claimedSource) && !lastObserved.equals(claimedSource)) {
            mitigationModule.blockClient(actualClientId, clientIp, socket);
            loggingModule.logAttackEvent(actualClientId, message, "ABNORMAL SEQUENCE TRANSITION",
                    "Sequence transition " + lastObserved + " -> " + claimedSource + " violates MSG topology", "CONNECTION BLOCKED");
            result = DetectionResult.blocked("ABNORMAL SEQUENCE TRANSITION",
                    "Transition " + lastObserved + " -> " + claimedSource + " does not exist in MSG topology");
            notifyListeners(actualClientId, message, result);
            return result;
        }

        if (!allowedPatterns.contains(pattern)) {
            mitigationModule.blockClient(actualClientId, clientIp, socket);
            loggingModule.logAttackEvent(actualClientId, message, "ABNORMAL COMMUNICATION",
                    "Message pattern " + pattern + " not present in learned MSG", "CONNECTION BLOCKED");
            result = DetectionResult.blocked("ABNORMAL COMMUNICATION",
                    "Message pattern does not match learned normal graph");
            notifyListeners(actualClientId, message, result);
            return result;
        }

        // Step 4: compare payload values with the source ECU's learned signal statistics.
        SignalAnomalyResult signalCheck = checkSignalAnomalies(claimedSource, message.getData());
        if (signalCheck.isAnomalous()) {
            mitigationModule.blockClient(actualClientId, clientIp, socket);
            loggingModule.logAttackEvent(actualClientId, message, "SIGNAL ANOMALY DETECTED",
                    signalCheck.getReason(), "CONNECTION BLOCKED");
            result = DetectionResult.blocked("SIGNAL ANOMALY DETECTED", signalCheck.getReason());
            notifyListeners(actualClientId, message, result);
            return result;
        }

        // Accept the message and record the observed transition.
        msgGraph.recordSequenceTransition(claimedSource, destination);
        loggingModule.logNormalMessage(actualClientId, message, "VALID", "Message accepted");
        result = DetectionResult.valid("VALID", "Message accepted");
        notifyListeners(actualClientId, message, result);
        return result;
    }

    private void extractAndRecordSignals(String ecuId, String data) {
        if (data == null || !data.contains("=")) return;
        String[] pairs = data.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                try {
                    double val = Double.parseDouble(kv[1].trim());
                    msgGraph.recordSignalValue(ecuId, kv[0].trim(), val);
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private SignalAnomalyResult checkSignalAnomalies(String ecuId, String data) {
        if (data == null || !data.contains("=")) return SignalAnomalyResult.normal();
        String[] pairs = data.split(",");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2) {
                String name = kv[0].trim();
                try {
                    double val = Double.parseDouble(kv[1].trim());
                    SignalStats stats = msgGraph.getSignalStats(ecuId, name);
                    if (stats != null && stats.getCount() >= 3) {
                        double zScore = stats.calculateZScore(val);
                        if (zScore > zScoreThreshold) {
                            return SignalAnomalyResult.anomaly(String.format(
                                    "Signal '%s' value %.2f deviates significantly from normal (mean=%.2f, std=%.2f, Z=%.2f > %.1f)",
                                    name, val, stats.getMean(), stats.getStdDev(), zScore, zScoreThreshold));
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return SignalAnomalyResult.normal();
    }

    private String patternKey(int messageId, String sourceEcu, String destinationEcu) {
        return messageId + "|" + sourceEcu + "|" + destinationEcu;
    }

    public synchronized MessageSequenceGraph getMsgGraph() {
        return msgGraph;
    }

    public LoggingModule getLoggingModule() {
        return loggingModule;
    }

    public MitigationModule getMitigationModule() {
        return mitigationModule;
    }

    public synchronized String graphSummary() {
        return msgGraph.getGraphSummary();
    }

    private static class SignalAnomalyResult {
        private final boolean anomalous;
        private final String reason;

        private SignalAnomalyResult(boolean anomalous, String reason) {
            this.anomalous = anomalous;
            this.reason = reason;
        }

        public static SignalAnomalyResult normal() {
            return new SignalAnomalyResult(false, "");
        }

        public static SignalAnomalyResult anomaly(String reason) {
            return new SignalAnomalyResult(true, reason);
        }

        public boolean isAnomalous() {
            return anomalous;
        }

        public String getReason() {
            return reason;
        }
    }

    public record DetectionResult(boolean blocked, String status, String reason) {
        public static DetectionResult valid(String status, String reason) {
            return new DetectionResult(false, status, reason);
        }

        public static DetectionResult blocked(String status, String reason) {
            return new DetectionResult(true, status, reason);
        }
    }
}
