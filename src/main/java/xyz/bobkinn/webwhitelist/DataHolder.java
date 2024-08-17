package xyz.bobkinn.webwhitelist;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import xyz.bobkinn.indigodataio.gson.GsonData;

import java.util.*;

@Getter
@RequiredArgsConstructor
public class DataHolder implements MessageInfo {
    private final String type;
    private final String id;
    private final GsonData data;

    // fields that persist only on sending
    private Boolean success = null;
    private List<GsonData> errors = null;

    /**
     * Generates new data holder with random id and desired type. Note that state is not success
     * @param type message type
     * @return new data holder
     */
    public static DataHolder newEmpty(String type){
        var id = UUID.randomUUID();
        return new DataHolder(type, id.toString(), new GsonData());
    }

    public static DataHolder ofSuccess(String type, String id){
        return ofSuccess(type, id, new GsonData());
    }

    public static DataHolder ofSuccess(MessageInfo request){
        return ofSuccess(request.getType(), request.getId());
    }

    public static DataHolder ofSuccess(MessageInfo request, GsonData payload){
        return ofSuccess(request.getType(), request.getId(), payload);
    }

    public static DataHolder ofSuccess(String type, String id, GsonData payload){
        var ret = new DataHolder(type, id, payload);
        ret.success = true;
        return ret;
    }

    private static void recurseException(Throwable t, List<GsonData> ls, int depth){
        if (depth >= 100) return;
        if (t == null) return;
        var map = new GsonData();
        map.put("type", t.getClass().getName());
        var msg = t.getMessage();
        if (msg != null) map.put("msg", msg);
        ls.add(map);
        var cause = t.getCause();
        if (cause != null && depth != -1) {
            recurseException(cause, ls, depth+1);
        }
    }

    public static DataHolder ofError(String type, String id, Exception e, GsonData extra){
        return ofError(type, id, e, true, extra);
    }

    public static DataHolder ofError(MessageInfo request, Exception e, GsonData extra){
        return ofError(request, e, true, extra);
    }

    public static DataHolder ofError(MessageInfo request, Exception e){
        return ofError(request.getType(), request.getId(), e);
    }

    public static DataHolder ofError(String type, String id, Exception e){
        return ofError(type, id, e, new GsonData());
    }

    public static DataHolder ofError(MessageInfo request, Exception e, boolean recurse, GsonData extra){
        return ofError(request.getType(), request.getId(), e, recurse, extra);
    }

    public static DataHolder ofError(String type, String id, Exception e, boolean recurse, GsonData extra){
        var ret = new DataHolder(type, id, extra);
        ret.success = false;
        if (e != null) {
            var errors = new ArrayList<GsonData>();
            recurseException(e, errors, recurse ? 0 : -1);
            ret.errors = errors;
        }
        return ret;
    }
}
