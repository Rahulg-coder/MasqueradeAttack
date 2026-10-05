package com.canids.server;

import com.canids.model.CANMessage;

/**
 * Event Listener interface for real-time IDS message inspection callbacks.
 */
public interface IDSListener {
    void onMessageInspected(String actualClientId, CANMessage message, CANIntrusionDetectionSystem.DetectionResult result);
}
