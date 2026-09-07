package app.notenote.todo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.work.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReviewWorker extends Worker {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    public static volatile String activeId="";
    public static boolean busy() { return BUSY.get(); }
    public ReviewWorker(@NonNull Context c,@NonNull WorkerParameters p) { super(c,p); }
    public static void schedule(Context c) {
        Config config=new Config(c);
        WorkManager manager=WorkManager.getInstance(c);
        // A local reminder pass needs no network and still works without an AI connection.
        PeriodicWorkRequest reminder=new PeriodicWorkRequest.Builder(ReviewWorker.class,15,TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putBoolean("remindersOnly",true).build()).build();
        manager.enqueueUniquePeriodicWork("note-reminders",ExistingPeriodicWorkPolicy.KEEP,reminder);
        if(config.prefs.getBoolean("enabled",false)) {
            PeriodicWorkRequest request=new PeriodicWorkRequest.Builder(ReviewWorker.class,config.prefs.getInt("interval",6),TimeUnit.HOURS)
                .setInitialDelay(config.prefs.getInt("interval",6),TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
            manager.enqueueUniquePeriodicWork("note-review",ExistingPeriodicWorkPolicy.UPDATE,request);
        } else manager.cancelUniqueWork("note-review");
    }
    public static void now(Context c,String id) {
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(ReviewWorker.class)
            .setInputData(new Data.Builder().putBoolean("manual",true).putString("taskId",id).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        WorkManager.getInstance(c).enqueueUniqueWork("note-review-manual",ExistingWorkPolicy.KEEP,request);
    }
    public static boolean notify(Context c,String title,String body,String id) {
        NotificationManager m=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false;
        if(!m.areNotificationsEnabled()) return false;
        String channel="note-progress";
        m.createNotificationChannel(new NotificationChannel(channel,"进展与待办提醒",NotificationManager.IMPORTANCE_DEFAULT));
        Intent i=new Intent(c,MainActivity.class).putExtra("taskId",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,id.hashCode(),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,channel).setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
            .setContentText(body).setVisibility(Notification.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending).build();
        m.notify(id.hashCode(),n); return true;
    }
    @NonNull public Result doWork() {
        if(!BUSY.compareAndSet(false,true)) return Result.retry();
        Config config=new Config(getApplicationContext()); Store store=Store.get(getApplicationContext());
        boolean remindersOnly=getInputData().getBoolean("remindersOnly",false);
        try {
            boolean manual=getInputData().getBoolean("manual",false);
            int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            boolean quiet=Rules.quiet(hour,config.prefs.getInt("quietFrom",22),config.prefs.getInt("quietTo",8));
            if(!manual && quiet) return Result.success();
            JSONArray all=store.all(); long now=System.currentTimeMillis(); int completed=0,errors=0;
            if(remindersOnly) {
                for(int i=0;i<all.length();i++) {
                    JSONObject t=all.getJSONObject(i);
                    if(t.optString("kind").equals("action") && Rules.reminderEligible(t.optBoolean("done"),t.optLong("due"),t.optLong("lastReminder"),t.optLong("snooze"),now)) {
                        if(notify(getApplicationContext(),"一件待办等你推进",t.getString("text"),t.getString("id"))) store.reminderSent(t.getString("id"),now);
                    }
                }
                return Result.success();
            }
            if(!manual && !config.prefs.getBoolean("enabled",false)) return Result.success();
            if(!config.ready()) { status(config,"等待连接模型"); return Result.success(); }
            String requested=getInputData().getString("taskId");
            AiClient ai=new AiClient(config);
            for(int i=all.length()-1;i>=0 && completed+errors<3;i--) {
                if(isStopped()) break;
                JSONObject t=all.getJSONObject(i);
                if(requested!=null && !requested.isEmpty() && !requested.equals(t.getString("id"))) continue;
                if(!Rules.reviewEligible(t.optString("kind"),t.optBoolean("done"),t.optLong("lastReview"),t.optLong("snooze"),now)) continue;
                activeId=t.getString("id");
                try {
                    JSONObject result=ai.review(t);
                    if(isStopped()) break;
                    if(store.result(activeId,t.optInt("revision"),result,"")) completed++;
                } catch(Exception e) {
                    if(isStopped()) break;
                    String message=e instanceof IllegalStateException||e instanceof IllegalArgumentException ? e.getMessage() : "连接失败，请检查网络和服务配置后重试";
                    store.result(activeId,t.optInt("revision"),null,message); errors++;
                    break; // An auth, quota or provider failure should not charge the remaining notes.
                }
            }
            status(config,completed>0?"新增 "+completed+" 条思考进展"+(errors>0?"，另有请求失败":""):errors>0?"本轮连接失败，查看记录中的原因":"本轮没有新的待推进问题");
            if(!quiet && completed>0) notify(getApplicationContext(),"有 "+completed+" 条新的思考进展","我往前推进了一步，来看看。","");
            return Result.success();
        } catch(Exception e) {
            if(!remindersOnly) status(config,"本轮未完成，记录仍已保留");
            return Result.failure();
        } finally { activeId=""; BUSY.set(false); }
    }
    private static void status(Config c,String text) { c.prefs.edit().putLong("lastRun",System.currentTimeMillis()).putString("lastStatus",text).apply(); }
}
