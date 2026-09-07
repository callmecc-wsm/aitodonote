package app.notenote.todo;
import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class StoreTest {
 private Store store; private String id;
 @Before public void setUp()throws Exception{store=Store.get(InstrumentationRegistry.getInstrumentation().getTargetContext());JSONObject t=store.save(new JSONObject().put("text","为什么模型可以推理？").put("kind","think"));id=t.getString("id");}
 @After public void clean(){if(id!=null)store.delete(id);}
 @Test public void originalSurvivesAiAndCompletion()throws Exception{JSONObject t=store.find(id);assertTrue(store.result(id,t.getInt("revision"),new JSONObject().put("role","assistant").put("detail","进一步思路"),""));assertEquals("为什么模型可以推理？",store.find(id).getString("text"));assertFalse(store.find(id).getBoolean("done"));store.change(id,"done","");assertTrue(store.find(id).getBoolean("done"));}
 @Test public void staleAiCannotOverwriteEdits()throws Exception{int rev=store.find(id).getInt("revision");store.save(new JSONObject().put("id",id).put("text","修改后的问题").put("kind","think"));assertFalse(store.result(id,rev,new JSONObject().put("detail","旧结果"),""));assertEquals(0,store.find(id).getJSONArray("events").length());}
 @Test public void deletedRecordIsNotRecreatedByAi()throws Exception{int rev=store.find(id).getInt("revision");store.delete(id);assertFalse(store.result(id,rev,new JSONObject(),""));assertNull(store.find(id));}
 @Test public void repliesTriggerNextReview()throws Exception{JSONObject t=store.find(id);store.result(id,t.getInt("revision"),new JSONObject().put("detail","分析"),"");store.change(id,"reply","希望从基础讲起");assertEquals(0,store.find(id).getLong("lastReview"));assertEquals(2,store.find(id).getJSONArray("events").length());}
 @Test public void backupImportKeepsLocalEdits()throws Exception{String backup=store.backup().toString();store.save(new JSONObject().put("id",id).put("text","新的本地内容").put("kind","note"));assertEquals(0,store.restore(backup));assertEquals("新的本地内容",store.find(id).getString("text"));assertFalse(backup.contains("apiKey"));}
 @Test public void keyStoredEncryptedAndNotInPublicSnapshot()throws Exception{Config c=new Config(InstrumentationRegistry.getInstrumentation().getTargetContext());try{c.save(new JSONObject().put("baseUrl","https://example.com/v1").put("model","test").put("apiKey","test-secret-123").put("interval",6));assertEquals("test-secret-123",c.secret("apiKey"));assertFalse(c.prefs.getString("apiKey","").contains("test-secret-123"));assertFalse(c.publicState().toString().contains("test-secret-123"));}finally{c.prefs.edit().clear().commit();}}
}
