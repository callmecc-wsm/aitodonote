package app.notenote.todo;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.*;
import android.view.WindowInsets;
import android.widget.Toast;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private WebView web;
    private Store store;
    private Config config;
    private volatile String notice="";
    private volatile String focusId="";
    private volatile String sharedText="";
    private volatile boolean testing=false;
    private static final String ORIGIN="https://app.notenote.local";
    private final java.util.concurrent.ExecutorService io=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=Store.get(this); config=new Config(this);
        web=new WebView(this); web.setBackgroundColor(Color.rgb(247,248,244));
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false); settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                Uri u=request.getUrl();
                if(!"https".equals(u.getScheme()) || !"app.notenote.local".equals(u.getHost())) return blocked();
                String path=u.getPath();
                if(path==null || path.equals("/")) path="/index.html";
                if(!path.matches("/(index\\.html|app\\.js|app\\.css|core\\.js)")) return blocked();
                try {
                    String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":"text/html";
                    return new WebResourceResponse(mime,"UTF-8",getAssets().open(path.substring(1)));
                } catch(IOException e) { return blocked(); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r) {
                if(r.isForMainFrame() && r.hasGesture() && "https".equals(r.getUrl().getScheme())) openExternal(r.getUrl().toString());
                return true;
            }
            private WebResourceResponse blocked() { return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0])); }
        });
        setContentView(web);
        web.setOnApplyWindowInsetsListener((view,insets)->{
            if(Build.VERSION.SDK_INT>=30) {
                android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                view.setPadding(bars.left,bars.top,bars.right,bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        web.loadUrl(ORIGIN+"/index.html"); readIntent(getIntent()); ReviewWorker.schedule(this);
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); readIntent(intent); }
    private void readIntent(Intent intent) {
        focusId=intent.getStringExtra("taskId")==null?"":intent.getStringExtra("taskId");
        if(Intent.ACTION_SEND.equals(intent.getAction()) && "text/plain".equals(intent.getType())) {
            String text=intent.getStringExtra(Intent.EXTRA_TEXT);
            if(text!=null) sharedText=text.substring(0,Math.min(text.length(),10000));
        }
    }
    private void openExternal(String url) {
        try {
            java.net.URI u=java.net.URI.create(url);
            if(!"https".equals(u.getScheme()) || u.getHost()==null || u.getUserInfo()!=null) return;
            startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
        } catch(Exception e) { notice="没有可用的浏览器"; }
    }
    private void requestNotifications() {
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3);
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
    }
    @Override public void onBackPressed() { web.evaluateJavascript("window.handleBack && window.handleBack()",value->{ if(!"true".equals(value)) super.onBackPressed(); }); }
    @Override protected void onDestroy() { web.removeJavascriptInterface("Native"); web.destroy(); io.shutdown(); super.onDestroy(); }

    private final class Bridge {
        @JavascriptInterface public String call(String method,String json) {
            try {
                JSONObject p=new JSONObject(json); JSONObject result=new JSONObject();
                switch(method) {
                    case "snapshot":
                        result.put("tasks",store.all()).put("config",config.publicState()).put("native",true)
                            .put("busy",ReviewWorker.busy()).put("activeId",ReviewWorker.activeId).put("testing",testing)
                            .put("notifications",((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled())
                            .put("focusId",focusId).put("sharedText",sharedText).put("notice",notice);
                        focusId=""; sharedText=""; notice=""; break;
                    case "save": result.put("task",store.save(p)); break;
                    case "change": store.change(p.getString("id"),p.getString("action"),p.optString("content")); break;
                    case "delete": store.delete(p.getString("id")); break;
                    case "settings": config.save(p); store.clearErrors(); ReviewWorker.schedule(MainActivity.this); break;
                    case "review":
                        if(!config.ready()) throw new IllegalArgumentException("先在设置中连接模型");
                        ReviewWorker.now(MainActivity.this,p.optString("id")); break;
                    case "test":
                        if(!config.ready()) throw new IllegalArgumentException("请先保存模型设置");
                        if(testing) break;
                        testing=true;
                        io.execute(()->{try{notice=new AiClient(config).test();}catch(Exception e){notice=e instanceof IllegalStateException?e.getMessage():"连接失败，请检查网络、接口地址和 API Key";}finally{testing=false;}}); break;
                    case "notifications": runOnUiThread(()->requestNotifications()); break;
                    case "export": runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"NoteNote-backup.json"),1)); break;
                    case "import": runOnUiThread(()->startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),2)); break;
                    case "open": runOnUiThread(()->openExternal(p.optString("url"))); break;
                    default: throw new IllegalArgumentException("不支持的操作");
                }
                return new JSONObject().put("ok",true).put("data",result).toString();
            } catch(Exception e) {
                String message=e instanceof IllegalArgumentException || e instanceof IllegalStateException?e.getMessage():"操作未完成，请重试";
                try{return new JSONObject().put("ok",false).put("error",message).toString();}catch(Exception ignored){return "{\"ok\":false}";}
            }
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null) return;
        Uri uri=data.getData();
        io.execute(()->{
            try {
                if(request==1) {
                    try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                        if(out==null) throw new IOException(); out.write(store.backup().toString(2).getBytes(StandardCharsets.UTF_8));
                    }
                    notice="备份已导出，不包含 API Key";
                } else if(request==2) {
                    try(InputStream in=getContentResolver().openInputStream(uri); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                        if(in==null) throw new IOException(); byte[] buf=new byte[8192]; int n;
                        while((n=in.read(buf))!=-1) { if(out.size()+n>20_000_000) throw new IllegalArgumentException("备份不能超过 20 MB"); out.write(buf,0,n); }
                        int count=store.restore(out.toString(StandardCharsets.UTF_8.name())); notice="已导入 "+count+" 条记录，已有记录保留";
                    }
                }
            } catch(Exception e) { notice=e instanceof IllegalArgumentException?e.getMessage():"文件处理失败，请检查文件后重试"; }
        });
    }
}
