package xyz.bobkinn.webwhitelist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

public record ActionLog(long timestamp, Action action, Set<String> players) {
    @Getter
    @RequiredArgsConstructor
    public enum Action {
        ADDED("status-log-added"),
        REMOVED("status-log-removed"),
        CONNECTED, DISCONNECTED, FAILED_TO_CONNECT;

        private final String additionalKey;

        Action(){
            this(null);
        }
    }
}
