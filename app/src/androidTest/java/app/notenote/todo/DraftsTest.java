package app.notenote.todo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DraftsTest extends DeviceTestBase {
    @Test public void scopedDraftsSurviveReopenWithoutChangingNotesOrBackup() throws Exception {
        String a=note("原问题甲","think").getString("id"),b=note("原问题乙","think").getString("id");
        Drafts drafts=new Drafts(context);
        drafts.saveTask("edit",a,new JSONObject().put("text","未保存的编辑").put("kind","note").put("searchAllowed",false));
        drafts.saveTask("reply",a,new JSONObject().put("text","甲的回复").put("apiKey","not-a-field"));
        drafts.saveTask("reply",b,new JSONObject().put("text","乙的回复"));
        JSONObject entries=new Drafts(context).workspace().getJSONObject("entries");
        assertEquals("未保存的编辑",entries.getJSONObject("edit:"+a).getString("text"));
        assertFalse(entries.getJSONObject("edit:"+a).getBoolean("searchAllowed"));
        assertEquals("甲的回复",entries.getJSONObject("reply:"+a).getString("text"));
        assertEquals("乙的回复",entries.getJSONObject("reply:"+b).getString("text"));
        assertFalse(entries.toString().contains("apiKey"));
        assertEquals("原问题甲",store.find(a).getString("text"));
        assertFalse(store.backup().toString().contains("未保存的编辑"));
        assertFalse(store.backup().toString().contains("甲的回复"));
    }
    @Test public void clearAndLeaveAffectOnlyTheIntendedDraft() throws Exception {
        String id=note("原问题","think").getString("id"); Drafts d=new Drafts(context);
        d.saveTask("edit",id,new JSONObject().put("text","编辑"));
        d.saveTask("reply",id,new JSONObject().put("text","补充"));
        d.leave();assertEquals(0,d.workspace().getJSONObject("resume").length());
        assertEquals(2,d.workspace().getJSONObject("entries").length());
        d.clearTask("edit",id);
        assertTrue(d.workspace().getJSONObject("entries").has("reply:"+id));
        d.deleteTask(id);assertEquals(0,d.workspace().getJSONObject("entries").length());
    }
    @Test public void shareMovesAtomicallyOnlyWhenComposerIsEmpty() throws Exception {
        Drafts d=new Drafts(context); d.save(new JSONObject().put("text","原来的草稿"));
        d.addShare("第一条分享");d.addShare("第二条分享");
        String id=d.workspace().getJSONArray("shares").getJSONObject(0).getString("id");
        try { d.takeShare(id); fail("overwrote draft"); } catch(IllegalStateException expected) {}
        assertEquals("原来的草稿",d.read().getString("text"));
        assertEquals(2,d.workspace().getJSONArray("shares").length());
        d.save(new JSONObject());d.takeShare(id);
        Drafts reopened=new Drafts(context);
        assertEquals("第一条分享",reopened.read().getString("text"));
        assertEquals("第二条分享",reopened.workspace().getJSONArray("shares").getJSONObject(0).getString("text"));
        assertEquals(0,store.all().length());
    }
    @Test public void invalidDraftNeverReplacesSavedText() throws Exception {
        String id=note("原文","note").getString("id"); Drafts d=new Drafts(context);
        d.saveTask("edit",id,new JSONObject().put("text","保留"));
        try { d.saveTask("edit",id,new JSONObject().put("text",new String(new char[10001]).replace('\0','x')));fail("too long"); }
        catch(IllegalArgumentException expected) {}
        assertEquals("保留",d.workspace().getJSONObject("entries").getJSONObject("edit:"+id).getString("text"));
    }
    @Test public void backupRoundTripPreservesInboxOrderAndExistingContent() throws Exception {
        String a=note("早一点","note").getString("id"),b=note("晚一点","note").getString("id");
        String backup=store.backup().toString();store.delete(a);store.delete(b);store.restore(backup);
        assertEquals(b,store.all().getJSONObject(0).getString("id"));
        assertEquals(a,store.all().getJSONObject(1).getString("id"));
        store.save(new JSONObject().put("id",b).put("text","本机更新"));
        assertEquals(0,store.restore(backup));assertEquals("本机更新",store.find(b).getString("text"));
    }
}
