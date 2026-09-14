package app.notenote.todo;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ReviewWorker extends Worker {
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    public static volatile String activeId="";
    public static boolean busy() { return BUSY.get(); }
    public ReviewWorker(@NonNull Context c,@NonNull WorkerParameters p) { super(c,p); }
    public static void schedule(Context c) {
        Config config=new Config(c); WorkManager manager=WorkManager.getInstance(c);
        PeriodicWorkRequest reminder=new PeriodicWorkRequest.Builder(ReviewWorker.class,15,TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putBoolean("remindersOnly",true).build()).build();
        manager.enqueueUniquePeriodicWork("note-reminders",ExistingPeriodicWorkPolicy.KEEP,reminder);
        if(config.prefs.getBoolean("enabled",false)) {
            PeriodicWorkRequest review=new PeriodicWorkRequest.Builder(ReviewWorker.class,config.prefs.getInt("interval",6),TimeUnit.HOURS)
                .setInitialDelay(config.prefs.getInt("interval",6),TimeUnit.HOURS)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
            manager.enqueueUniquePeriodicWork("note-review",ExistingPeriodicWorkPolicy.UPDATE,review);
        } else manager.cancelUniqueWork("note-review");
    }
    public static void now(Context c,String id) {
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(ReviewWorker.class)
            .setInputData(new Data.Builder().putBoolean("manual",true).putString("taskId",id).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS)
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        // Distinct notes keep their requests; repeated taps on the same note are coalesced.
        WorkManager.getInstance(c).enqueueUniqueWork("note-review-manual-"+id,ExistingWorkPolicy.KEEP,request);
    }
    private boolean quiet(Config c) {
        return Rules.quiet(Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            c.prefs.getInt("quietFrom",22),c.prefs.getInt("quietTo",8));
    }
    private void flush(Store store,Config config) throws Exception {
        Notifications.flush(store,quiet(config),System.currentTimeMillis(),(t,p)->Notifications.send(getApplicationContext(),t,p));
    }
    @NonNull public Result doWork() {
        Config config=new Config(getApplicationContext()); Store store=Store.get(getApplicationContext());
        boolean remindersOnly=getInputData().getBoolean("remindersOnly",false);
        // Local reminders never wait behind a slow model request.
        if(remindersOnly) {
            try { flush(store,config); return Result.success(); }
            catch(Exception e) { return Result.retry(); }
        }
        if(!BUSY.compareAndSet(false,true)) return Result.retry();
        try {
            boolean manual=getInputData().getBoolean("manual",false);
            if(!manual&&(quiet(config)||!config.prefs.getBoolean("enabled",false))) return Result.success();
            if(!config.ready()) { config.recordRun("等待连接模型",null); return Result.success(); }
            if(!manual&&!config.prefs.getString("blocked","").isEmpty()) return Result.success();
            if(manual) { config.prefs.edit().remove("blocked").commit(); store.clearErrors(); }
            int revision=config.prefs.getInt("revision",0);
            AiClient client=new AiClient(config);
            ReviewEngine.Report report=ReviewEngine.run(store,client::review,
                ()->isStopped()||revision!=config.prefs.getInt("revision",0),
                id->activeId=id,getInputData().getString("taskId"),System.currentTimeMillis());
            if(report.failed>0) config.prefs.edit().putString("blocked",report.error).commit();
            String status=report.failed>0?"连接失败，自动推进已暂停；检查设置后点立即推进"
                :report.completed>0?"新增 "+report.completed+" 条思考进展"
                :report.discarded>0?"记录或设置已变更，旧结果已丢弃":"本轮没有到期的待推进问题";
            config.recordRun(status,report);
            flush(store,config);
            return Result.success();
        } catch(Exception e) {
            config.recordRun("本轮中断，记录已保留；下次回顾将继续",null);
            return Result.retry();
        } finally { activeId=""; BUSY.set(false); }
    }
}
