package app.notenote.todo;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LaunchTest {
 private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int n=0;n<g.getChildCount();n++){WebView w=find(g.getChildAt(n));if(w!=null)return w;}}return null;}
 private String js(android.app.Instrumentation i,WebView w,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);AtomicReference<String> result=new AtomicReference<>("");i.runOnMainSync(()->w.evaluateJavascript(script,r->{result.set(r);done.countDown();}));assertTrue("WebView callback timed out",done.await(5,TimeUnit.SECONDS));return result.get();}
 @Test public void appRendersAndCapturesNativeRecord()throws Exception{
  android.app.Instrumentation i=InstrumentationRegistry.getInstrumentation();
  Intent intent=new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  Activity a=i.startActivitySync(intent);assertNotNull(a);
  AtomicReference<WebView> ref=new AtomicReference<>();i.runOnMainSync(()->ref.set(find(a.getWindow().getDecorView())));WebView w=ref.get();assertNotNull(w);
  String body="";long deadline=SystemClock.elapsedRealtime()+20000;
  while(SystemClock.elapsedRealtime()<deadline){body=js(i,w,"document.body.innerText");if(body.contains("先记下来"))break;SystemClock.sleep(250);}
  assertTrue("Home screen did not render: "+body,body.contains("先记下来"));
  assertFalse("Android must not use browser preview adapter",body.contains("界面预览"));
  js(i,w,"document.querySelector('#capture-text').value='界面测试：为什么模型能够推理';document.querySelector('#capture-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));true");
  org.json.JSONArray tasks=Store.get(i.getTargetContext()).all();String id=null;
  for(int n=0;n<tasks.length();n++){org.json.JSONObject t=tasks.getJSONObject(n);if(t.getString("text").equals("界面测试：为什么模型能够推理")){id=t.getString("id");assertEquals("think",t.getString("kind"));}}
  assertNotNull("Capture did not reach SQLite",id);Store.get(i.getTargetContext()).delete(id);
  js(i,w,"document.querySelector('[data-tab=\"settings\"]').click();true");
  assertTrue(js(i,w,"document.body.innerText").contains("连接你的 AI"));
  i.runOnMainSync(a::finish);
 }
}
