package xyz.bobkinn.webwhitelist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

@Getter
@RequiredArgsConstructor
public class DataHolder implements MessageInfo {
    private final String type;
    private final String id;
    private final Map<String, Object> data;

    /**
     * Generates new data holder with random id and desired type
     * @param type message type
     * @return new data holder
     */
    public static DataHolder newEmpty(String type){
        var id = UUID.randomUUID();
        return new DataHolder(type, id.toString(), Map.of());
    }

    public static DataHolder ofSuccess(String type, String id){
        return new DataHolder(type, id, Map.of("success", true));
    }

    public static DataHolder ofSuccess(MessageInfo request){
        return ofSuccess(request.getType(), request.getId());
    }

    public static DataHolder ofSuccess(MessageInfo request, Map<String, Object> payload){
        return ofSuccess(request.getType(), request.getId(), payload);
    }

    public static DataHolder ofSuccess(String type, String id, Map<String, Object> payload){
        Map<String, Object> data = new HashMap<>(payload);
        data.put("success", true);
        return new DataHolder(type, id, data);
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

    public static DataHolder ofError(String type, String id, Exception e, Map<String, Object> extra){
        return ofError(type, id, e, true, extra);
    }

    public static DataHolder ofError(MessageInfo request, Exception e, Map<String, Object> extra){
        return ofError(request, e, true, extra);
    }

    public static DataHolder ofError(MessageInfo request, Exception e){
        return ofError(request.getType(), request.getId(), e);
    }

    public static DataHolder ofError(String type, String id, Exception e){
        return ofError(type, id, e, Map.of());
    }

    public static DataHolder ofError(MessageInfo request, Exception e, boolean recurse, Map<String, Object> extra){
        return ofError(request.getType(), request.getId(), e, recurse, extra);
    }

    public static DataHolder ofError(String type, String id, Exception e, boolean recurse, Map<String, Object> extra){
        var map = new HashMap<>(extra);
        map.put("success", false);
        if (e != null) {
            var errors = new ArrayList<Map<String, Object>>();
            recurseException(e, errors, recurse ? 0 : -1);
            map.put("errors", errors);
        }
        return new DataHolder(type, id, map);
    }
}
