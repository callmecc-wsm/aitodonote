package app.notenote.todo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NotificationTest extends DeviceTestBase {
    @Test public void quietAndDeniedDeliveryPreservePendingProgress() throws Exception {
        JSONObject t=note("问题","think"); String id=t.getString("id");
        store.result(id,t.getInt("revision"),event("enough",""),"");
        long now=System.currentTimeMillis();
        assertEquals(0,Notifications.flush(store,true,now,(x,p)->{fail("quiet");return true;}));
        assertEquals(0,Notifications.flush(store,false,now,(x,p)->false));
        assertFalse(store.find(id).getString("pendingNotification").isEmpty());
        assertEquals(1,Notifications.flush(store,false,now,(x,p)->{assertTrue(p);return true;}));
        assertEquals(0,Notifications.flush(store,false,now,(x,p)->{fail("duplicate");return true;}));
        assertTrue(store.find(id).getBoolean("unread"));
        store.markRead(id); assertFalse(store.find(id).getBoolean("unread"));
    }
    @Test public void staleAcknowledgementCannotHideANewerResult() throws Exception {
        JSONObject t=note("问题","think"); String id=t.getString("id");
        JSONObject first=event("enough",""); store.result(id,t.getInt("revision"),first,"");
        JSONObject second=event("enough",""); store.result(id,t.getInt("revision"),second,"");
        store.notificationSent(id,first.getString("id"));
        assertEquals(second.getString("id"),store.find(id).getString("pendingNotification"));
    }
    @Test public void readingAnOlderSnapshotCannotHideANewerAnswer() throws Exception {
        JSONObject t=note("问题","think"); String id=t.getString("id");
        JSONObject first=event("enough",""); store.result(id,t.getInt("revision"),first,"");
        JSONObject second=event("enough",""); store.result(id,t.getInt("revision"),second,"");
        assertFalse(store.markRead(id,first.getString("id")));
        assertTrue(store.find(id).getBoolean("unread"));
        assertTrue(store.markRead(id,second.getString("id")));
        assertFalse(store.find(id).getBoolean("unread"));
    }
    @Test public void completedAndReadProgressAreNotPushed() throws Exception {
        JSONObject a=note("问题一","think"),b=note("问题二","think");
        store.result(a.getString("id"),a.getInt("revision"),event("enough",""),"");
        store.result(b.getString("id"),b.getInt("revision"),event("enough",""),"");
        store.change(a.getString("id"),"complete",""); store.markRead(b.getString("id"));
        assertEquals(0,Notifications.flush(store,false,System.currentTimeMillis(),(x,p)->{fail("already handled");return true;}));
    }
    @Test public void offlineRemindersAreAcknowledgedOnlyAfterDelivery() throws Exception {
        JSONObject t=store.save(new JSONObject().put("text","办理银行卡").put("kind","action").put("due",1));
        long now=System.currentTimeMillis();
        assertEquals(0,Notifications.flush(store,false,now,(x,p)->false));
        assertEquals(0,store.find(t.getString("id")).getLong("lastReminder"));
        assertEquals(1,Notifications.flush(store,false,now,(x,p)->{assertFalse(p);return true;}));
        assertEquals(0,Notifications.flush(store,false,now+1000,(x,p)->{fail("too frequent");return true;}));
    }
    @Test public void repeatedCompleteActionCannotReopenTask() throws Exception {
        JSONObject t=note("去办卡","action");
        Intent intent=new Intent("complete").putExtra("taskId",t.getString("id")).putExtra("revision",t.getInt("revision"));
        NotificationReceiver.apply(context,intent); NotificationReceiver.apply(context,intent);
        assertTrue(store.find(t.getString("id")).getBoolean("done"));
    }
    @Test public void oldNotificationCannotModifyEditedTask() throws Exception {
        JSONObject t=note("去办卡","action");
        Intent intent=new Intent("complete").putExtra("taskId",t.getString("id")).putExtra("revision",t.getInt("revision"));
        store.save(new JSONObject().put("id",t.getString("id")).put("text","改天再办卡").put("kind","action"));
        NotificationReceiver.apply(context,intent);
        assertFalse(store.find(t.getString("id")).getBoolean("done"));
    }
    @Test public void realSystemNotificationHasIsolatedImmutableActions() throws Exception {
        if(Build.VERSION.SDK_INT>=33) InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .grantRuntimePermission(context.getPackageName(),Manifest.permission.POST_NOTIFICATIONS);
        JSONObject first=note("办理第一张卡","action"),second=note("办理第二张卡","action");
        assertTrue(Notifications.send(context,first,false)); assertTrue(Notifications.send(context,second,false));
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        android.service.notification.StatusBarNotification[] active=manager.getActiveNotifications();
        long postedDeadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(active.length!=2&&android.os.SystemClock.elapsedRealtime()<postedDeadline) {
            android.os.SystemClock.sleep(50); active=manager.getActiveNotifications();
        }
        assertEquals("Both notifications must reach the asynchronous system service",2,active.length);
        for(android.service.notification.StatusBarNotification entry:active) {
            Notification n=entry.getNotification();
            assertEquals(2,n.actions.length); assertNotNull(n.contentIntent);
            assertEquals(Notification.VISIBILITY_PRIVATE,n.visibility);
        }
        assertNotEquals(Notifications.action(context,first,"complete"),Notifications.action(context,second,"complete"));
        Notifications.action(context,first,"complete").send();
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        while(!store.find(first.getString("id")).optBoolean("done")&&android.os.SystemClock.elapsedRealtime()<deadline)
            android.os.SystemClock.sleep(50);
        assertTrue(store.find(first.getString("id")).getBoolean("done"));
        assertFalse(store.find(second.getString("id")).getBoolean("done"));
    }
}
