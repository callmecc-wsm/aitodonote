package app.notenote.todo;

import android.Manifest;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.WorkManager;
import androidx.work.testing.TestWorkerBuilder;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class WorkerTest extends DeviceTestBase {
    private ReviewWorker worker(Data input) {
        return TestWorkerBuilder.from(context,ReviewWorker.class,(java.util.concurrent.Executor)Runnable::run).setInputData(input).build();
    }
    @Test public void reminderWorkerWorksWithoutAnyModelConfiguration() throws Exception {
        if(Build.VERSION.SDK_INT>=33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .grantRuntimePermission(context.getPackageName(),Manifest.permission.POST_NOTIFICATIONS);
        config.prefs.edit().putInt("quietFrom",0).putInt("quietTo",0).commit();
        String id=store.save(new JSONObject().put("text","线下办卡").put("kind","action").put("due",1)).getString("id");
        assertEquals(ListenableWorker.Result.success(),worker(new Data.Builder().putBoolean("remindersOnly",true).build()).doWork());
        assertTrue(store.find(id).getLong("lastReminder")>0);
        assertEquals(0,store.find(id).getJSONArray("events").length());
    }
    @Test public void disabledAutomaticReviewLeavesNotesUntouched() throws Exception {
        String id=note("问题","think").getString("id");
        assertEquals(ListenableWorker.Result.success(),worker(Data.EMPTY).doWork());
        assertEquals(0,store.find(id).getLong("lastReview")); assertFalse(ReviewWorker.busy());
    }
    @Test public void pausedProviderDoesNotRunAgainAutomatically() throws Exception {
        connect(false); note("问题","think");
        config.prefs.edit().putBoolean("enabled",true).putString("blocked","鉴权失败").putLong("lastRun",123).commit();
        assertEquals(ListenableWorker.Result.success(),worker(Data.EMPTY).doWork());
        assertEquals(123,config.prefs.getLong("lastRun",0)); assertFalse(ReviewWorker.busy());
    }
    @Test public void separateManualNotesKeepSeparateQueuedRequests() throws Exception {
        String a=note("问题 A","think").getString("id"),b=note("问题 B","think").getString("id");
        ReviewWorker.now(context,a); ReviewWorker.now(context,b); ReviewWorker.now(context,a);
        WorkManager manager=WorkManager.getInstance(context);
        assertEquals(1,manager.getWorkInfosForUniqueWork("note-review-manual-"+a).get().size());
        assertEquals(1,manager.getWorkInfosForUniqueWork("note-review-manual-"+b).get().size());
    }
}
