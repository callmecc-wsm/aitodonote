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
        WebView w=ref.get(); assertNotNull(w); long deadline=SystemClock.elapsedRealtime()+20000;
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
}
