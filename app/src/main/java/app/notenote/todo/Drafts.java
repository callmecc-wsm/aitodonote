package app.notenote.todo;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

final class Drafts {
    private final SharedPreferences prefs;
    Drafts(Context c) { prefs=c.getSharedPreferences("drafts",Context.MODE_PRIVATE); }
    JSONObject read() throws Exception { return new JSONObject(prefs.getString("capture","{}")); }
    void save(JSONObject input) throws Exception {
        String text=input.optString("text"),kind=input.optString("kind","auto"),due=input.optString("due");
        if(text.length()>10000||due.length()>30) throw new IllegalArgumentException("草稿内容过长");
        if(!kind.equals("auto")&&!kind.equals("think")&&!kind.equals("action")&&!kind.equals("note"))
            throw new IllegalArgumentException("草稿类型不正确");
        JSONObject draft=new JSONObject().put("text",text).put("kind",kind).put("due",due)
            .put("searchAllowed",input.optBoolean("searchAllowed",true));
        if(!prefs.edit().putString("capture",draft.toString()).commit()) throw new IllegalStateException("草稿保存失败");
    }
}
