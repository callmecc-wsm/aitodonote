package app.notenote.todo;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UiFlowTest extends DeviceTestBase {
    private void screenshot(String name) throws Exception {
        android.graphics.Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull("Device screenshot must be available",bitmap);
        java.io.File dir=new java.io.File(context.getExternalFilesDir(null),"ui-evidence");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } finally { bitmap.recycle(); }
        // UTP removes app-scoped external files when uninstalling the test APKs.
        shell("mkdir -p /sdcard/Download/NoteNoteEvidence");
        shell("cp "+new java.io.File(dir,name+".png").getAbsolutePath()+" /sdcard/Download/NoteNoteEvidence/"+name+".png");
    }
    private void shell(String command) throws Exception {
        android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buffer=new byte[1024];java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();int count;
            while((count=in.read(buffer))!=-1)output.write(buffer,0,count);
            assertEquals("Screenshot copy must succeed","",output.toString("UTF-8").trim());
        }
    }
    private WebView find(View v) {
        if(v instanceof WebView) return (WebView)v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) {
            WebView w=find(((ViewGroup)v).getChildAt(i)); if(w!=null) return w;
        }
        return null;
    }
    private String js(WebView w,String script) throws Exception {
        CountDownLatch done=new CountDownLatch(1); AtomicReference<String> result=new AtomicReference<>("");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->w.evaluateJavascript(script,r->{result.set(r);done.countDown();}));
        assertTrue(done.await(5,TimeUnit.SECONDS)); return result.get();
    }
    private WebView ready(ActivityScenario<MainActivity> scenario) throws Exception {
        AtomicReference<WebView> ref=new AtomicReference<>();
        scenario.onActivity(a->ref.set(find(a.getWindow().getDecorView())));
        return ready(ref.get());
    }
    private WebView ready(WebView w) throws Exception {
        assertNotNull(w); long deadline=SystemClock.elapsedRealtime()+20000;
        while(SystemClock.elapsedRealtime()<deadline) {
            if(js(w,"Boolean(document.querySelector('#capture-text'))").equals("true")) return w;
            SystemClock.sleep(100);
        }
        fail("UI did not render"); return w;
    }
    @Test public void captureDraftSurvivesActivityRecreationAndClearsAfterSave() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            js(w,"document.querySelector('#capture-text').value='尚未保存的问题';document.querySelector('#capture-text').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('[data-kind=think]').click();document.querySelector('#capture-search').checked=false;document.querySelector('#capture-search').dispatchEvent(new Event('input',{bubbles:true}));true");
            scenario.recreate(); w=ready(scenario);
            assertTrue(js(w,"document.querySelector('#capture-text').value").contains("尚未保存的问题"));
            assertEquals("false",js(w,"document.querySelector('#capture-search').checked"));
            screenshot("01-capture-restored");
            assertEquals("\"think\"",js(w,"document.querySelector('.type-button.selected').dataset.kind"));
            js(w,"document.querySelector('#capture-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");
            assertEquals(1,store.all().length()); assertFalse(store.all().getJSONObject(0).getBoolean("searchAllowed"));
            assertEquals("",new Drafts(context).read().getString("text"));
        }
    }
    @Test public void progressRendersUntrustedTextAndReadStateReachesDatabase() throws Exception {
        JSONObject t=note("<img src=x onerror=alert(1)> 为什么","think");
        store.result(t.getString("id"),t.getInt("revision"),event("ask_user","想先看哪个方向？").put("detail","<script>window.injected=true</script>"),"");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            js(w,"document.querySelector('.card').click();true");
            assertEquals("Backdrop must cover the visible WebView","true",js(w,"(function(){var r=document.querySelector('.modal-backdrop').getBoundingClientRect();return Math.abs(r.top)<2&&Math.abs(r.left)<2&&Math.abs(r.width-innerWidth)<2&&Math.abs(r.height-innerHeight)<2;})()"));
            assertEquals("Detail sheet must fit the visible viewport","true",js(w,"(function(){var r=document.querySelector('.modal').getBoundingClientRect();return r.height>100&&r.top>=0&&r.bottom<=innerHeight+1;})()"));
            assertEquals("Original markup must remain literal text","true",js(w,"document.querySelector('.detail-text').textContent.startsWith('<img')"));
            assertEquals("0",js(w,"document.querySelector('.detail-text').querySelectorAll('img').length"));
            assertEquals("false",js(w,"Boolean(window.injected)"));
            assertTrue("Detail must explain that AI is waiting for input",js(w,"document.body.innerText").contains("等你补充"));
            assertFalse(store.find(t.getString("id")).getBoolean("unread"));
            js(w,"document.querySelector('[data-act=close]').click();document.querySelector('[data-tab=progress]').click();true");
            assertEquals("0",js(w,"document.querySelectorAll('.nav-badge').length"));
        }
    }

    @Test public void editDraftRestoresSheetAfterRecreationAndCommitsOnlyOnSave() throws Exception {
        JSONObject t=note("原问题","think");String id=t.getString("id");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            js(w,"document.querySelector('.card').click();document.querySelector('[data-act=edit]').click();document.querySelector('#edit-text').value='编辑到一半';document.querySelector('#edit-text').dispatchEvent(new Event('input',{bubbles:true}));true");
            assertEquals("原问题",store.find(id).getString("text"));
            scenario.recreate();w=ready(scenario);
            assertEquals("true",js(w,"document.querySelector('#edit-text').value==='编辑到一半'"));
            screenshot("02-edit-restored");
            js(w,"document.querySelector('#edit-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");
            assertEquals("编辑到一半",store.find(id).getString("text"));
            assertFalse(new Drafts(context).workspace().getJSONObject("entries").has("edit:"+id));
            assertEquals("false",js(w,"document.body.classList.contains('dialog-open')"));
        }
    }
    @Test public void repliesStaySeparateAcrossNotesAndSurviveRecreation() throws Exception {
        String a=note("第一个问题","think").getString("id"),b=note("第二个问题","think").getString("id");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            js(w,"showDetail('"+a+"');document.querySelector('#reply-text').value='甲的补充';document.querySelector('#reply-text').dispatchEvent(new Event('input',{bubbles:true}));close();showDetail('"+b+"');true");
            assertEquals("true",js(w,"document.querySelector('#reply-text').value===''"));
            js(w,"document.querySelector('#reply-text').value='乙的补充';document.querySelector('#reply-text').dispatchEvent(new Event('input',{bubbles:true}));true");
            scenario.recreate();w=ready(scenario);
            assertEquals("true",js(w,"document.querySelector('#reply-text').value==='乙的补充'"));
            screenshot("03-reply-restored");
            assertEquals(0,store.find(a).getJSONArray("events").length());
            js(w,"document.querySelector('#reply-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");
            assertEquals("乙的补充",store.find(b).getJSONArray("events").getJSONObject(0).getString("detail"));
            assertFalse(new Drafts(context).workspace().getJSONObject("entries").has("reply:"+b));
            js(w,"close();showDetail('"+a+"');true");
            assertEquals("true",js(w,"document.querySelector('#reply-text').value==='甲的补充'"));
        }
    }
    @Test public void androidSharePreservesDraftAndIsNotDuplicatedByRecreation() throws Exception {
        new Drafts(context).save(new JSONObject().put("text","先写下的草稿"));
        android.content.Intent intent=new android.content.Intent(context,MainActivity.class)
            .setAction(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT,"分享进来的问题");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(intent)) {
            WebView w=ready(scenario);scenario.recreate();w=ready(scenario);
            assertEquals("true",js(w,"document.querySelector('#capture-text').value==='先写下的草稿'"));
            assertEquals(1,new Drafts(context).workspace().getJSONArray("shares").length());
            screenshot("04-share-preserves-draft");
            js(w,"document.querySelector('[data-act=take-share]').click();true");
            assertEquals("true",js(w,"document.querySelector('#capture-text').value==='先写下的草稿'"));
            assertEquals(0,store.all().length());
            js(w,"document.querySelector('#capture-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));document.querySelector('[data-act=take-share]').click();true");
            assertEquals("true",js(w,"document.querySelector('#capture-text').value==='分享进来的问题'"));
            assertEquals(0,new Drafts(context).workspace().getJSONArray("shares").length());
            assertEquals(1,store.all().length());
        }
    }
    @Test public void notificationContinueActionOpensMatchingReplyAndKeepsOtherDraft() throws Exception {
        JSONObject a=note("另一个问题","think"),b=note("通知中的问题","think");
        new Drafts(context).saveTask("reply",a.getString("id"),new JSONObject().put("text","另一条的草稿"));
        // ActivityScenario matches lifecycle events against the original Intent. This test
        // deliberately replaces that Intent through a real notification, so own cleanup.
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.app.Activity activity=instrumentation.startActivitySync(new android.content.Intent(context,MainActivity.class)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            AtomicReference<WebView> ref=new AtomicReference<>();
            instrumentation.runOnMainSync(()->ref.set(find(activity.getWindow().getDecorView())));
            WebView w=ready(ref.get());
            Notifications.open(context,b,true).send();
            long deadline=SystemClock.elapsedRealtime()+10000;boolean opened=false;
            while(SystemClock.elapsedRealtime()<deadline) {
                if(js(w,"Boolean(document.querySelector('#reply-form')&&document.querySelector('#reply-form').dataset.id==='"+b.getString("id")+"'&&document.activeElement.id==='reply-text')").equals("true")) {opened=true;break;}
                SystemClock.sleep(100);
            }
            assertTrue("Notification must focus reply on its own note",opened);
            screenshot("05-notification-reply");
            assertTrue(new Drafts(context).workspace().getJSONObject("entries").has("reply:"+a.getString("id")));
        } finally { instrumentation.runOnMainSync(activity::finish);instrumentation.waitForIdleSync(); }
    }
    @Test public void backgroundRefreshKeepsReadingPositionInLongProgress() throws Exception {
        JSONObject t=note("一个很长的研究问题","think");String id=t.getString("id");
        store.result(id,t.getInt("revision"),event("enough","").put("detail",new String(new char[4000]).replace('\0','文')),"");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            js(w,"document.querySelector('.card').click();document.querySelector('.modal').scrollTop=250;true");
            assertEquals("true",js(w,"document.querySelector('.modal').scrollTop>200"));
            js(w,"lastSignature='';refresh();true");
            assertEquals("true",js(w,"document.querySelector('.modal').scrollTop>200"));
        }
    }
}
