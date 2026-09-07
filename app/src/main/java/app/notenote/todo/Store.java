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

/** All task changes are transactional; results merge only into the captured revision. */
public final class Store extends SQLiteOpenHelper {
    private static Store instance;
    public static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }
    private Store(Context c) { super(c, "notenote.db", null, 1); }
    public void onCreate(SQLiteDatabase db) { db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, body TEXT NOT NULL)"); }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Missing migration"); }
    public synchronized JSONArray all() throws JSONException {
        JSONArray a = new JSONArray();
        try (Cursor cur = getReadableDatabase().rawQuery("SELECT body FROM tasks ORDER BY rowid DESC", null)) {
            while (cur.moveToNext()) a.put(new JSONObject(cur.getString(0)));
        }
        return a;
    }
    public synchronized JSONObject find(String id) throws JSONException {
        try (Cursor cur = getReadableDatabase().rawQuery("SELECT body FROM tasks WHERE id=?", new String[]{id})) {
            return cur.moveToFirst() ? new JSONObject(cur.getString(0)) : null;
        }
    }
    private void put(JSONObject t) throws JSONException {
        ContentValues v = new ContentValues(); v.put("id", t.getString("id")); v.put("body", t.toString());
        getWritableDatabase().insertWithOnConflict("tasks", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public synchronized JSONObject save(JSONObject input) throws JSONException {
        String text = input.optString("text").trim();
        if (text.isEmpty() || text.length() > 10000) throw new IllegalArgumentException("记录需为 1–10,000 个字符");
        JSONObject old = find(input.optString("id"));
        JSONObject t = old == null ? new JSONObject() : old;
        long now = System.currentTimeMillis();
        if (old == null) {
            t.put("id", UUID.randomUUID().toString()).put("created", now).put("done", false)
                .put("events", new JSONArray()).put("lastReminder", 0).put("snooze", 0);
        }
        String requested = input.optString("kind", "auto");
        String kind = Rules.classify(text, requested);
        t.put("text", text).put("kind", kind).put("updated", now).put("revision", t.optInt("revision") + 1)
            .put("lastReview", 0).put("error", "").put("due", Math.max(0, input.optLong("due")));
        if (kind.equals("action") && t.optLong("due") == 0) t.put("due", now + Rules.DAY);
        put(t); return t;
    }
    public synchronized void change(String id, String action, String content) throws JSONException {
        JSONObject t = find(id); if (t == null) throw new IllegalArgumentException("记录已不存在");
        switch (action) {
            case "done": t.put("done", !t.optBoolean("done")); break;
            case "snooze": t.put("snooze", System.currentTimeMillis() + Rules.DAY); break;
            case "resume": t.put("snooze", 0); break;
            case "retry": t.put("lastReview", 0).put("error", "").put("snooze", 0); break;
            case "reply":
                content = content.trim();
                if (content.isEmpty() || content.length() > 10000) throw new IllegalArgumentException("补充需为 1–10,000 个字符");
                t.getJSONArray("events").put(new JSONObject().put("id", UUID.randomUUID().toString())
                    .put("role", "user").put("time", System.currentTimeMillis()).put("detail", content));
                t.put("lastReview", 0).put("error", "").put("snooze", 0); break;
            default: throw new IllegalArgumentException("不支持的操作");
        }
        t.put("revision", t.optInt("revision") + 1).put("updated", System.currentTimeMillis()); put(t);
    }
    public synchronized void delete(String id) { getWritableDatabase().delete("tasks", "id=?", new String[]{id}); }
    public synchronized boolean result(String id, int revision, JSONObject event, String error) throws JSONException {
        JSONObject t = find(id);
        if (t == null || t.optBoolean("done") || t.optInt("revision") != revision) return false;
        if (event != null) {
            t.getJSONArray("events").put(event); t.put("lastReview", System.currentTimeMillis());
        } else {
            // Stop repeated automatic API charges after errors; explicit retry/settings edit re-enables.
            t.put("lastReview", System.currentTimeMillis());
        }
        t.put("error", error); put(t); return true;
    }
    public synchronized void reminderSent(String id, long time) throws JSONException {
        JSONObject t = find(id); if (t != null) { t.put("lastReminder", time); put(t); }
    }
    public synchronized void clearErrors() throws JSONException {
        JSONArray a = all();
        for (int i=0; i<a.length(); i++) {
            JSONObject t=a.getJSONObject(i);
            if (!t.optString("error").isEmpty()) { t.put("error", "").put("lastReview", 0); put(t); }
        }
    }
    public synchronized JSONObject backup() throws JSONException {
        return new JSONObject().put("format", "notenote-backup").put("version", 1)
            .put("exported", System.currentTimeMillis()).put("tasks", all());
    }
    public synchronized int restore(String source) throws JSONException {
        JSONObject root = new JSONObject(source);
        if (!root.optString("format").equals("notenote-backup") || root.optInt("version") != 1)
            throw new IllegalArgumentException("这不是 Note Note 备份文件");
        JSONArray list = root.getJSONArray("tasks");
        if (list.length() > 10000) throw new IllegalArgumentException("单次最多导入 10,000 条");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction(); int count = 0;
        try {
            for (int i=0; i<list.length(); i++) {
                JSONObject t=list.getJSONObject(i);
                UUID.fromString(t.getString("id"));
                if (t.getString("text").length() > 10000 || t.getString("text").trim().isEmpty()) throw new IllegalArgumentException("备份内容格式有误");
                String kind=t.getString("kind");
                if (!kind.equals("think") && !kind.equals("action") && !kind.equals("note")) throw new IllegalArgumentException("记录类型不正确");
                t.getJSONArray("events");
                // Merge missing ids only. Existing notes and newer local edits are preserved.
                if (find(t.getString("id")) == null) { put(t); count++; }
            }
            db.setTransactionSuccessful(); return count;
        } finally { db.endTransaction(); }
    }
}
