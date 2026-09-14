package app.notenote.todo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Explicit, private, idempotent notification actions. */
public final class NotificationReceiver extends BroadcastReceiver {
    private static final java.util.concurrent.ExecutorService IO=java.util.concurrent.Executors.newSingleThreadExecutor();
    @Override public void onReceive(Context context,Intent intent) {
        if(!"complete".equals(intent.getAction())&&!"snooze".equals(intent.getAction())) return;
        PendingResult pending=goAsync();
        IO.execute(()->{try { apply(context,intent); } catch(Exception ignored) {
            // Leave task state intact; the user can still open the record.
        } finally { pending.finish(); }});
    }
    static void apply(Context c,Intent intent) throws Exception {
        String id=intent.getStringExtra("taskId");
        if(id==null) return;
        Store store=Store.get(c);
        synchronized(store) {
            org.json.JSONObject t=store.find(id);
            if(t==null||t.optInt("revision")!=intent.getIntExtra("revision",-1)) return;
            if(!"complete".equals(intent.getAction())&&!"snooze".equals(intent.getAction())) return;
            store.change(id,intent.getAction(),""); Notifications.cancel(c,id);
        }
    }
}
