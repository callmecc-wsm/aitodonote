package app.notenote.todo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local drafts never enter model requests or exports before the user saves them. */
final class Drafts {
    private static final Object LOCK=new Object();
    private final SharedPreferences prefs;
    Drafts(Context c) { prefs=c.getSharedPreferences("drafts",Context.MODE_PRIVATE); }
    JSONObject read() throws Exception { synchronized(LOCK) { return object("capture"); } }
    private JSONObject object(String key) throws Exception { return new JSONObject(prefs.getString(key,"{}")); }
    private void commit(SharedPreferences.Editor editor) {
        if(!editor.commit()) throw new IllegalStateException("草稿保存失败，请稍后重试");
    }
    private JSONObject clean(JSONObject input,boolean reply) throws Exception {
        String text=input.optString("text"),kind=input.optString("kind","auto"),due=input.optString("due");
        if(text.length()>10000||due.length()>30) throw new IllegalArgumentException("草稿内容过长");
        if(!kind.equals("auto")&&!kind.equals("think")&&!kind.equals("action")&&!kind.equals("note"))
            throw new IllegalArgumentException("草稿类型不正确");
        JSONObject result=new JSONObject().put("text",text);
        if(!reply) result.put("kind",kind).put("due",due).put("searchAllowed",input.optBoolean("searchAllowed",true));
        return result;
    }
    void save(JSONObject input) throws Exception { synchronized(LOCK) {
        commit(prefs.edit().putString("capture",clean(input,false).toString()));
    } }
    JSONObject workspace() throws Exception { synchronized(LOCK) {
        JSONObject entries=new JSONObject();
        for(String key:prefs.getAll().keySet()) if(key.startsWith("edit:")||key.startsWith("reply:"))
            entries.put(key,object(key));
        return new JSONObject().put("entries",entries).put("resume",object("resume"))
            .put("shares",new JSONArray(prefs.getString("shares","[]")));
    } }
    private String key(String scope,String id) {
        if(!scope.equals("edit")&&!scope.equals("reply")) throw new IllegalArgumentException("草稿类型不正确");
        if(id==null||id.length()!=36) throw new IllegalArgumentException("记录编号格式有误");
        java.util.UUID.fromString(id); return scope+":"+id;
    }
    void saveTask(String scope,String id,JSONObject input) throws Exception { synchronized(LOCK) {
        String k=key(scope,id);
        JSONObject entry=clean(input,scope.equals("reply"));
        // Reopening a draft never saves it into the original note implicitly.
        commit(prefs.edit().putString(k,entry.toString()).putString("resume",
            new JSONObject().put("scope",scope).put("id",id).toString()));
    } }
    void clearTask(String scope,String id) { synchronized(LOCK) {
        commit(prefs.edit().remove(key(scope,id)).remove("resume"));
    } }
    void leave() { synchronized(LOCK) { commit(prefs.edit().remove("resume")); } }
    void deleteTask(String id) { synchronized(LOCK) {
        commit(prefs.edit().remove(key("edit",id)).remove(key("reply",id)).remove("resume"));
    } }
    void addShare(String text) throws Exception { synchronized(LOCK) {
        JSONArray shares=new JSONArray(prefs.getString("shares","[]"));
        if(shares.length()>=50) throw new IllegalStateException("已有 50 条分享待接收，请先处理收件箱中的分享");
        shares.put(new JSONObject().put("id",java.util.UUID.randomUUID().toString())
            .put("text",text.substring(0,Math.min(text.length(),10000))));
        commit(prefs.edit().putString("shares",shares.toString()));
    } }
    JSONObject takeShare(String id) throws Exception { synchronized(LOCK) {
        // Move into an empty composer atomically; never overwrite a capture draft.
        if(!read().optString("text").trim().isEmpty()) throw new IllegalStateException("先保存当前草稿，再接收分享");
        JSONArray shares=new JSONArray(prefs.getString("shares","[]")),rest=new JSONArray(); JSONObject chosen=null;
        for(int i=0;i<shares.length();i++) {
            JSONObject item=shares.getJSONObject(i);
            if(item.getString("id").equals(id)) chosen=item; else rest.put(item);
        }
        if(chosen==null) throw new IllegalArgumentException("这条分享已接收");
        JSONObject draft=clean(new JSONObject().put("text",chosen.getString("text")),false);
        commit(prefs.edit().putString("capture",draft.toString()).putString("shares",rest.toString()));
        return draft;
    } }
}
