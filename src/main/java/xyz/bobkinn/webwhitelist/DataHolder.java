package xyz.bobkinn.webwhitelist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public class DataHolder {
    private final String type;
    private final Map<String, Object> data;

    public static DataHolder ofSuccess(String type){
        return new DataHolder(type, Map.of("success", true));
    }

    public static DataHolder ofSuccess(String type, Map<String, Object> payload){
        Map<String, Object> data = new HashMap<>(payload);
        data.put("success", true);
        return new DataHolder(type, data);
    }

    private static void recurseException(Throwable t, List<Map<String, Object>> ls, int depth){
        if (depth >= 100) return;
        if (t == null) return;
        var map = new HashMap<String, Object>();
        map.put("type", t.getClass().getName());
        var msg = t.getMessage();
        if (msg != null) map.put("msg", msg);
        ls.add(map);
        var cause = t.getCause();
        if (cause != null && depth != -1) {
            recurseException(cause, ls, depth+1);
        }
    }

    public static DataHolder ofError(String type, Exception e, Map<String, Object> extra){
        return ofError(type, e, true, extra);
    }

    public static DataHolder ofError(String type, Exception e){
        return ofError(type, e, true, Map.of());
    }

    public static DataHolder ofError(String type, Exception e, boolean recurse, Map<String, Object> extra){
        var map = new HashMap<>(extra);
        map.put("success", false);
        if (e != null) {
            var errors = new ArrayList<Map<String, Object>>();
            recurseException(e, errors, recurse ? 0 : -1);
            map.put("errors", errors);
        }
        return new DataHolder(type, map);
    }
}
