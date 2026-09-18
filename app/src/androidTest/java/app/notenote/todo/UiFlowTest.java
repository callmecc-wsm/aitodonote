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
            js(w,"document.querySelector('#capture-text').value='尚未保存的问题';document.querySelector('#capture-text').dispatchEvent(new Event('input',{bubbles:true}));document.querySelector('#capture-form [data-kind=think]').click();document.querySelector('#capture-search').checked=false;document.querySelector('#capture-search').dispatchEvent(new Event('input',{bubbles:true}));true");
            scenario.recreate(); w=ready(scenario);
            assertTrue(js(w,"document.querySelector('#capture-text').value").contains("尚未保存的问题"));
            assertEquals("false",js(w,"document.querySelector('#capture-search').checked"));
            DeviceScreenshots.capture(context,w,"01-capture-restored");
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
            DeviceScreenshots.capture(context,w,"02-edit-restored");
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
            DeviceScreenshots.capture(context,w,"03-reply-restored");
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
            DeviceScreenshots.capture(context,w,"04-share-preserves-draft");
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
            DeviceScreenshots.capture(context,w,"05-notification-reply");
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
    @Test public void redesignedScreensKeepRecordsReadableAndNavigationFunctional() throws Exception {
        note("周末想找个安静的地方，读完手边这本书。","note");
        JSONObject action=store.save(new JSONObject().put("text","周六下午整理阳台，给绿植换盆").put("kind","action").put("due",System.currentTimeMillis()+86400000));
        JSONObject thought=note("模型是怎么一步步学会回答问题的？想先弄懂训练过程。","think");
        store.result(thought.getString("id"),thought.getInt("revision"),event("ask_user","你想先了解预训练，还是模型如何学会遵循指令？")
            .put("summary","可以先把训练理解成三个阶段")
            .put("detail","预训练，让模型从大量文本里学习语言和知识。\n\n指令微调，让它学会按问题和任务来组织回答。\n\n后训练，再通过反馈改进回答的质量和取舍。每个阶段解决的问题不同，值得分开看。")
            .put("research","测试样例 · 未联网检索"),"");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            assertEquals("true",js(w,"document.querySelectorAll('.card').length===3&&!document.querySelector('.hero')"));
            assertEquals("Capture and tabs must fit without overlap","true",js(w,"(function(){var d=document.querySelector('.capture-dock').getBoundingClientRect(),n=document.querySelector('.bottom-nav').getBoundingClientRect();return d.top>100&&d.bottom<=n.top+1&&n.bottom<=innerHeight+1&&document.documentElement.scrollWidth<=innerWidth;})()"));
            DeviceScreenshots.capture(context,w,"06-inbox-with-records");
            js(w,"document.querySelector('[data-act=search-open]').click();document.querySelector('#search').value='阳台';document.querySelector('#search').dispatchEvent(new Event('input',{bubbles:true}));true");
            assertEquals("true",js(w,"document.querySelectorAll('.card').length===1&&document.querySelector('.card-title').textContent.includes('阳台')"));
            DeviceScreenshots.capture(context,w,"12-search");
            js(w,"document.querySelector('[data-act=search-close]').click();document.querySelector('[data-filter=think]').click();true");
            assertEquals("true",js(w,"document.querySelectorAll('.card').length===1&&document.querySelector('.card-title').textContent.includes('模型')"));
            js(w,"document.querySelector('[data-tab=progress]').click();true");
            assertEquals("1",js(w,"document.querySelectorAll('.progress-card').length"));
            DeviceScreenshots.capture(context,w,"07-progress");
            js(w,"document.querySelector('.progress-card footer button').click();true");
            assertEquals("true",js(w,"document.querySelector('.detail-text').textContent.includes('模型')&&document.querySelectorAll('.event').length===1"));
            js(w,"document.querySelector('.modal').scrollTop=0;true");
            DeviceScreenshots.capture(context,w,"08-record-detail");
            js(w,"document.querySelector('[data-act=close]').click();document.querySelector('[data-tab=settings]').click();true");
            assertEquals("true",js(w,"document.querySelectorAll('.settings-group').length===5&&!document.querySelector('.settings-group[open]')"));
            DeviceScreenshots.capture(context,w,"09-settings");
            js(w,"document.querySelector('.settings-group summary').click();true");
            assertEquals("true",js(w,"document.querySelector('input[name=model]').getBoundingClientRect().height>=44"));
            DeviceScreenshots.capture(context,w,"10-model-settings");
            js(w,"document.querySelector('[data-tab=inbox]').click();showDetail('"+action.getString("id")+"');true");
            assertEquals("true",js(w,"!!document.querySelector('.due-info')&&!document.querySelector('#reply-form')"));
            DeviceScreenshots.capture(context,w,"11-action-detail");
        }
    }
    @Test public void captureExpansionKeepsDraftAndAllowsOneHandCollapse() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            WebView w=ready(scenario);
            assertEquals("false",js(w,"document.querySelector('.capture-dock').classList.contains('expanded')"));
            js(w,"document.querySelector('#capture-text').click();document.querySelector('#capture-text').focus();document.querySelector('#capture-text').value='写到一半的想法';document.querySelector('#capture-text').dispatchEvent(new Event('input',{bubbles:true}));true");
            assertEquals("true",js(w,"document.querySelector('.capture-dock').classList.contains('expanded')"));
            js(w,"document.querySelector('[data-act=collapse-compose]').click();true");
            assertEquals("false",js(w,"document.querySelector('.capture-dock').classList.contains('expanded')"));
            assertEquals("写到一半的想法",new Drafts(context).read().getString("text"));
        }
    }

}
