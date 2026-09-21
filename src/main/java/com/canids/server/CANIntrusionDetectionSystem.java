package com.canids.server;

import com.canids.model.CANMessage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CANIntrusionDetectionSystem {
    private final Set<String> registeredEcus = new HashSet<>();
    private final Set<String> allowedPatterns = new HashSet<>();
    private final Set<String> communicationEdges = new HashSet<>();
    private final Map<String, Integer> messageFrequency = new HashMap<>();

    public CANIntrusionDetectionSystem() {
        registerEcu("ECU_ENGINE");
        registerEcu("ECU_BRAKE");
        registerEcu("ECU_SENSOR");
        registerEcu("ECU_DASHBOARD");

        learnNormalPattern(100, "ECU_ENGINE", "ECU_DASHBOARD");
        learnNormalPattern(200, "ECU_BRAKE", "ECU_DASHBOARD");
        learnNormalPattern(300, "ECU_SENSOR", "ECU_DASHBOARD");
        learnNormalPattern(400, "ECU_ENGINE", "ECU_BRAKE");
    }

    public synchronized DetectionResult inspect(String actualClientId, CANMessage message) {
        String claimedSource = message.getSourceEcu();
        String pattern = patternKey(message.getMessageId(), claimedSource, message.getDestinationEcu());

        if (!registeredEcus.contains(actualClientId)) {
            return DetectionResult.blocked("MASQUERADE ATTACK DETECTED",
                    "Actual connection is from an unknown device: " + actualClientId);
        }

        if (!actualClientId.equals(claimedSource)) {
            return DetectionResult.blocked("MASQUERADE ATTACK DETECTED",
                    "Message claims to be from " + claimedSource + " but connection is " + actualClientId);
        }

        if (!registeredEcus.contains(message.getDestinationEcu())) {
            return DetectionResult.blocked("SUSPICIOUS DESTINATION",
                    "Destination ECU is not registered: " + message.getDestinationEcu());
        }

        if (!allowedPatterns.contains(pattern)) {
            return DetectionResult.blocked("ABNORMAL COMMUNICATION",
                    "Message does not match the learned normal communication graph");
        }

        communicationEdges.add(edgeKey(claimedSource, message.getDestinationEcu()));
        messageFrequency.merge(pattern, 1, Integer::sum);
        return DetectionResult.valid("VALID", "Message accepted");
    }

    public synchronized String graphSummary() {
        return "Graph nodes=" + registeredEcus + ", edges=" + communicationEdges.size();
    }

    private void registerEcu(String ecuId) {
        registeredEcus.add(ecuId);
    }

    private void learnNormalPattern(int messageId, String sourceEcu, String destinationEcu) {
        allowedPatterns.add(patternKey(messageId, sourceEcu, destinationEcu));
        communicationEdges.add(edgeKey(sourceEcu, destinationEcu));
    }

    private String patternKey(int messageId, String sourceEcu, String destinationEcu) {
        return messageId + "|" + sourceEcu + "|" + destinationEcu;
    }

    private String edgeKey(String sourceEcu, String destinationEcu) {
        return sourceEcu + "->" + destinationEcu;
    }

    public record DetectionResult(boolean blocked, String status, String reason) {
        static DetectionResult valid(String status, String reason) {
            return new DetectionResult(false, status, reason);
        }

        static DetectionResult blocked(String status, String reason) {
            return new DetectionResult(true, status, reason);
        }
    }
}
