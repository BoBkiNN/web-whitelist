package xyz.bobkinn.webwhitelist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

public record ModifyLog(long timestamp, Action action, Set<String> players) {
    @Getter
    @RequiredArgsConstructor
    public enum Action {
        ADD, REMOVE, CONNECT, DISCONNECTED, FAILED_TO_CONNECT;

        private final String additionalKey;

        Action(){
            this(null);
        }
    }
}
