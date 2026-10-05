package com.canids.mitigation;

import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SRS 3.1 & SRS 4.6.3 Mitigation Module:
 * Handles isolation and blocking of malicious / unauthorized clients.
 * Force closes malicious client socket links and purges network address records from active registry.
 */
public class MitigationModule {
    private final Set<String> blockedClientIds = ConcurrentHashMap.newKeySet();
    private final Set<String> blockedIpAddresses = ConcurrentHashMap.newKeySet();

    public void blockClient(String clientId, String ipAddress, Socket socket) {
        if (clientId != null && !clientId.trim().isEmpty()) {
            blockedClientIds.add(clientId.trim());
        }
        if (ipAddress != null && !ipAddress.trim().isEmpty()) {
            blockedIpAddresses.add(ipAddress.trim());
        }

        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                System.out.println("[Mitigation] Malicious socket link force-closed for client: " + clientId + " (" + ipAddress + ")");
            } catch (IOException e) {
                System.err.println("[Mitigation] Error closing socket: " + e.getMessage());
            }
        }
    }

    public boolean isBlocked(String clientId, String ipAddress) {
        if (clientId != null && blockedClientIds.contains(clientId.trim())) {
            return true;
        }
        if (ipAddress != null && blockedIpAddresses.contains(ipAddress.trim())) {
            return true;
        }
        return false;
    }

    public Set<String> getBlockedClientIds() {
        return Collections.unmodifiableSet(blockedClientIds);
    }

    public Set<String> getBlockedIpAddresses() {
        return Collections.unmodifiableSet(blockedIpAddresses);
    }

    public void unblockClient(String clientId) {
        blockedClientIds.remove(clientId);
    }
}
