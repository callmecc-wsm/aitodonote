package app.notenote.todo;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.WorkManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;

public abstract class DeviceTestBase {
    Context context; Store store; Config config;
    @Before public void resetDeviceState() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        WorkManager.getInstance(context).cancelAllWork().getResult().get();
        store=Store.get(context);
        store.getWritableDatabase().delete("tasks",null,null);
        config=new Config(context); config.prefs.edit().clear().commit();
        context.getSharedPreferences("drafts",Context.MODE_PRIVATE).edit().clear().commit();
        context.getSystemService(android.app.NotificationManager.class).cancelAll();
    }
    JSONObject note(String text,String kind) throws Exception {
        return store.save(new JSONObject().put("text",text).put("kind",kind));
    }
    JSONObject event(String next,String question) throws Exception {
        return new JSONObject().put("id",java.util.UUID.randomUUID().toString())
            .put("role","assistant").put("summary","一个新的判断").put("detail","进一步解释和一个具体行动。")
            .put("question",question).put("nextAction",next).put("nextQuery",next.equals("research")?"继续研究新的具体方向":"")
            .put("sources",new JSONArray());
    }
    void connect(boolean search) throws Exception {
        config.save(new JSONObject().put("baseUrl","https://example.com/v1").put("model","test-model")
            .put("apiKey","test-only-model-key").put("searchKey","test-only-search-key").put("search",search)
            .put("quietFrom",0).put("quietTo",0));
    }
    ReviewEngine.Report run(ReviewEngine.Reviewer reviewer,long now) throws Exception {
        return ReviewEngine.run(store,reviewer,()->false,id->{},"",now);
    }
}
