package xyz.bobkinn.webwhitelist;

import xyz.bobkinn.indigodataio.gson.GsonData;

public interface MessageHandler {
    void handle(GsonData data, MessageInfo msg);
}
