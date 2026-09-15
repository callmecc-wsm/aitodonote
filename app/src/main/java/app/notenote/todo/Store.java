package app.notenote.todo;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** SQLite owns task state. Every result is conditional on the user's captured revision. */
public final class Store extends SQLiteOpenHelper {
    private static Store instance;
    public static synchronized Store get(Context c) {
        if(instance==null) instance=new Store(c.getApplicationContext());
        return instance;
    }
    private Store(Context c) { super(c,"notenote.db",null,1); }
    public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, body TEXT NOT NULL)"); }
    public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { throw new IllegalStateException("Missing migration"); }
    public synchronized JSONArray all() throws JSONException {
        JSONArray a=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM tasks ORDER BY rowid DESC",null)) {
            while(c.moveToNext()) a.put(new JSONObject(c.getString(0)));
        }
        return a;
    }
    public synchronized JSONObject find(String id) throws JSONException {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT body FROM tasks WHERE id=?",new String[]{id})) {
            return c.moveToFirst()?new JSONObject(c.getString(0)):null;
        }
    }
    private void put(JSONObject t) throws JSONException {
        ContentValues v=new ContentValues(); v.put("body",t.toString());
        SQLiteDatabase db=getWritableDatabase();
        if(db.update("tasks",v,"id=?",new String[]{t.getString("id")})==0) {
            v.put("id",t.getString("id")); db.insertOrThrow("tasks",null,v);
        }
    }
    private static void reset(JSONObject t) throws JSONException {
        t.put("lastReview",0).put("nextReview",0).put("rounds",0).put("needsUser",false)
            .put("error","").put("nextQuery","").put("pendingNotification","");
    }
    public synchronized JSONObject save(JSONObject input) throws JSONException {
        String text=input.optString("text").trim();
        if(text.isEmpty()||text.length()>10000) throw new IllegalArgumentException("记录需为 1–10,000 个字符");
        JSONObject t=find(input.optString("id")); long now=System.currentTimeMillis();
        if(t==null) t=new JSONObject().put("id",UUID.randomUUID().toString()).put("created",now)
            .put("done",false).put("events",new JSONArray()).put("snooze",0).put("lastReminder",0);
        String kind=Rules.classify(text,input.optString("kind","auto"));
        long due=Math.max(0,input.optLong("due"));
        if(kind.equals("action")&&due==0) due=now+Rules.DAY;
        if(due!=t.optLong("due")) t.put("lastReminder",0);
        t.put("text",text).put("kind",kind).put("due",due).put("updated",now)
            .put("searchAllowed",input.optBoolean("searchAllowed",t.optBoolean("searchAllowed",true)))
            .put("revision",t.optInt("revision")+1);
        reset(t); put(t); return t;
    }
    public synchronized void change(String id,String action,String content) throws JSONException {
        JSONObject t=find(id); if(t==null) throw new IllegalArgumentException("记录已不存在");
        long now=System.currentTimeMillis();
        switch(action) {
            case "done": t.put("done",!t.optBoolean("done")); break;
            case "complete": if(t.optBoolean("done")) return; t.put("done",true); break;
            case "snooze": t.put("snooze",now+Rules.DAY); break;
            case "resume": t.put("snooze",0); break;
            case "retry": reset(t); t.put("snooze",0); break;
            case "reply":
                content=content.trim();
                if(content.isEmpty()||content.length()>10000) throw new IllegalArgumentException("补充需为 1–10,000 个字符");
                t.getJSONArray("events").put(new JSONObject().put("id",UUID.randomUUID().toString())
                    .put("role","user").put("time",now).put("detail",content));
                reset(t); t.put("snooze",0); break;
            default: throw new IllegalArgumentException("不支持的操作");
        }
        if(t.optBoolean("done")) t.put("pendingNotification","").put("unread",false);
        t.put("revision",t.optInt("revision")+1).put("updated",now); put(t);
    }
    public synchronized void delete(String id) { getWritableDatabase().delete("tasks","id=?",new String[]{id}); }
    public synchronized boolean result(String id,int revision,JSONObject event,String error) throws JSONException {
        return resultAt(id,revision,event,error,System.currentTimeMillis());
    }
    synchronized boolean resultAt(String id,int revision,JSONObject event,String error,long now) throws JSONException {
        JSONObject t=find(id);
        if(t==null||t.optBoolean("done")||t.optInt("revision")!=revision) return false;
        t.put("lastReview",now).put("error",error).put("nextReview",0);
        if(event!=null) {
            if(!event.has("id")) event.put("id",UUID.randomUUID().toString());
            event.put("role","assistant");
            if(!event.has("time")) event.put("time",now);
            t.getJSONArray("events").put(event);
            int rounds=t.optInt("rounds")+1;
            boolean needsUser=!event.optString("question").isEmpty()||event.optString("nextAction").equals("ask_user");
            boolean research=!needsUser&&event.optString("nextAction").equals("research")&&!event.optString("nextQuery").trim().isEmpty();
            t.put("rounds",rounds).put("needsUser",needsUser).put("unread",true)
                .put("pendingNotification",event.getString("id"))
                .put("nextQuery",research?event.optString("nextQuery"):"")
                .put("nextReview",research&&rounds<3?now+Rules.DAY:0);
        }
        put(t); return true;
    }
    public synchronized void markRead(String id) throws JSONException { markRead(id,null); }
    public synchronized boolean markRead(String id,String seenEventId) throws JSONException {
        JSONObject t=find(id); if(t==null) return false;
        JSONArray events=t.getJSONArray("events"); String newest="";
        for(int i=events.length()-1;i>=0;i--) {
            JSONObject e=events.getJSONObject(i);
            if(e.optString("role").equals("assistant")) { newest=e.optString("id"); break; }
        }
        if(seenEventId!=null&&!seenEventId.equals(newest)) return false;
        t.put("unread",false).put("pendingNotification",""); put(t); return true;
    }
    public synchronized void notificationSent(String id,String eventId) throws JSONException {
        JSONObject t=find(id);
        if(t!=null&&t.optString("pendingNotification").equals(eventId)) { t.put("pendingNotification",""); put(t); }
    }
    public synchronized void reminderSent(String id,long time) throws JSONException {
        JSONObject t=find(id); if(t!=null) { t.put("lastReminder",time); put(t); }
    }
    public synchronized void clearErrors() throws JSONException {
        JSONArray a=all();
        for(int i=0;i<a.length();i++) {
            JSONObject t=a.getJSONObject(i);
            if(!t.optString("error").isEmpty()) { reset(t); put(t); }
        }
    }
    public synchronized JSONObject backup() throws JSONException {
        return new JSONObject().put("format","notenote-backup").put("version",1)
            .put("exported",System.currentTimeMillis()).put("tasks",all());
    }
    private static String limited(JSONObject o,String field,int max,boolean required) throws JSONException {
        Object value=o.opt(field);
        if(value==null&&!required) return "";
        if(!(value instanceof String)) throw new IllegalArgumentException("备份文字格式有误");
        String text=(String)value;
        if(text.length()>max||(required&&text.trim().isEmpty())) throw new IllegalArgumentException("备份文字长度有误");
        return text;
    }
    private static long number(JSONObject o,String key,long fallback) {
        if(!o.has(key)) return fallback;
        Object value=o.opt(key);
        if(!(value instanceof Number)||((Number)value).doubleValue()<0
            ||((Number)value).doubleValue()!=((Number)value).longValue()) throw new IllegalArgumentException("备份时间或计数格式有误");
        return ((Number)value).longValue();
    }
    private static JSONObject cleanTask(JSONObject source) throws JSONException {
        String id=limited(source,"id",36,true); UUID.fromString(id);
        if(id.length()!=36) throw new IllegalArgumentException("记录编号格式有误");
        String kind=limited(source,"kind",10,true);
        if(!kind.equals("think")&&!kind.equals("action")&&!kind.equals("note")) throw new IllegalArgumentException("记录类型不正确");
        JSONObject t=new JSONObject().put("id",id).put("text",limited(source,"text",10000,true)).put("kind",kind);
        for(String key:new String[]{"created","updated","due","snooze","lastReminder","lastReview","nextReview","rounds","revision"})
            t.put(key,number(source,key,0));
        if(t.optLong("rounds")>3||t.optLong("revision")>Integer.MAX_VALUE) throw new IllegalArgumentException("备份计数超出范围");
        for(String key:new String[]{"done","needsUser","unread","searchAllowed"}) {
            if(source.has(key)&&!(source.get(key) instanceof Boolean)) throw new IllegalArgumentException("备份状态格式有误");
            t.put(key,source.optBoolean(key,key.equals("searchAllowed")));
        }
        t.put("error",limited(source,"error",500,false)).put("nextQuery",limited(source,"nextQuery",400,false))
            .put("pendingNotification","");
        JSONArray events=source.getJSONArray("events"),clean=new JSONArray();
        if(events.length()>1000) throw new IllegalArgumentException("单条记录最多导入 1,000 条进展");
        for(int i=0;i<events.length();i++) {
            JSONObject e=events.getJSONObject(i);
            String role=limited(e,"role",20,true);
            if(!role.equals("user")&&!role.equals("assistant")) throw new IllegalArgumentException("备份进展类型有误");
            JSONObject x=new JSONObject().put("id",e.has("id")?limited(e,"id",100,true):UUID.randomUUID().toString())
                .put("role",role).put("time",number(e,"time",0)).put("detail",limited(e,"detail",12000,true));
            for(String key:new String[]{"summary","question","research","nextAction","nextQuery"})
                x.put(key,limited(e,key,key.equals("question")?2000:1000,false));
            JSONArray sources=e.optJSONArray("sources"),links=new JSONArray();
            if(sources!=null) {
                if(sources.length()>5) throw new IllegalArgumentException("备份来源数量有误");
                for(int n=0;n<sources.length();n++) {
                    JSONObject s=sources.getJSONObject(n);
                    String url=limited(s,"url",4000,true);
                    if(!Rules.safeLink(url)) throw new IllegalArgumentException("备份包含无效来源链接");
                    links.put(new JSONObject().put("id",n+1).put("url",url).put("title",limited(s,"title",1000,false)));
                }
            }
            clean.put(x.put("sources",links));
        }
        return t.put("events",clean);
    }
    public synchronized int restore(String source) throws JSONException {
        JSONObject root=new JSONObject(source);
        if(!root.optString("format").equals("notenote-backup")||root.optInt("version")!=1)
            throw new IllegalArgumentException("这不是 Note Note 备份文件");
        JSONArray list=root.getJSONArray("tasks");
        if(list.length()>10000) throw new IllegalArgumentException("单次最多导入 10,000 条");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction(); int count=0;
        try {
            // Backups are newest first; insert oldest first to retain that order.
            for(int i=list.length()-1;i>=0;i--) {
                JSONObject t=cleanTask(list.getJSONObject(i));
                if(find(t.getString("id"))==null) { put(t); count++; }
            }
            db.setTransactionSuccessful(); return count;
        } finally { db.endTransaction(); }
    }
}
