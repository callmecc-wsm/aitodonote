package app.notenote.todo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecoveryTest extends DeviceTestBase {
    @Test public void updatesDoNotReorderCaptureHistory() throws Exception {
        String first=note("第一条","note").getString("id"),second=note("第二条","note").getString("id");
        store.change(first,"done","");
        assertEquals(second,store.all().getJSONObject(0).getString("id"));
    }
    @Test public void malformedImportRollsBackEveryEarlierInsertion() throws Exception {
        JSONObject good=note("有效记录","note");
        store.delete(good.getString("id"));
        JSONObject bad=new JSONObject(good.toString()).put("id",java.util.UUID.randomUUID().toString())
            .put("events",new JSONArray().put(new JSONObject().put("role","script").put("detail","invalid")));
        JSONObject backup=new JSONObject().put("format","notenote-backup").put("version",1).put("tasks",new JSONArray().put(good).put(bad));
        try { store.restore(backup.toString()); fail("invalid backup accepted"); } catch(IllegalArgumentException expected) {}
        assertEquals(0,store.all().length());
    }
    @Test public void restoreRemovesUnknownFieldsAndDoesNotSendOldNotifications() throws Exception {
        JSONObject t=note("原文","think"); String id=t.getString("id");
        t.put("injectedSettings",new JSONObject().put("apiKey","never-import")).put("pendingNotification","old");
        store.delete(id);
        String backup=new JSONObject().put("format","notenote-backup").put("version",1).put("tasks",new JSONArray().put(t)).toString();
        assertEquals(1,store.restore(backup));
        assertFalse(store.find(id).has("injectedSettings"));
        assertEquals("",store.find(id).getString("pendingNotification"));
    }
    @Test public void draftSurvivesANewRepositoryInstanceAndExcludesCredentials() throws Exception {
        new Drafts(context).save(new JSONObject().put("text","写到一半的想法").put("kind","think")
            .put("due","2026-10-01T09:00").put("searchAllowed",false).put("apiKey","never-save"));
        JSONObject restored=new Drafts(context).read();
        assertEquals("写到一半的想法",restored.getString("text")); assertEquals("think",restored.getString("kind"));
        assertFalse(restored.getBoolean("searchAllowed")); assertFalse(restored.has("apiKey"));
    }
    @Test public void changingQuietHoursDoesNotResetFailedRequests() throws Exception {
        connect(false); config.prefs.edit().putString("blocked","鉴权失败").commit();
        assertFalse(config.save(new JSONObject().put("baseUrl","https://example.com/v1").put("model","test-model").put("quietFrom",21)));
        assertEquals("鉴权失败",config.publicState().getString("blocked"));
    }
    @Test public void historyIsBoundedAndContainsOnlyRunMetadata() throws Exception {
        for(int i=0;i<25;i++) config.recordRun("第 "+i+" 轮",new ReviewEngine.Report());
        JSONArray history=config.publicState().getJSONArray("history");
        assertEquals(20,history.length()); assertEquals("第 24 轮",history.getJSONObject(0).getString("status"));
        assertFalse(history.toString().contains("apiKey"));
    }
}
