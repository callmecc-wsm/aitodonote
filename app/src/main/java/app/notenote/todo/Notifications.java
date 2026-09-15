package app.notenote.todo;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

final class Notifications {
    static final String CHANNEL="note-progress";
    interface Delivery { boolean send(JSONObject task,boolean progress) throws Exception; }
    static boolean available(Context c) {
        NotificationManager m=c.getSystemService(NotificationManager.class);
        m.createNotificationChannel(new NotificationChannel(CHANNEL,"进展与待办提醒",NotificationManager.IMPORTANCE_DEFAULT));
        return (Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)
            &&m.areNotificationsEnabled()&&m.getNotificationChannel(CHANNEL).getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static PendingIntent action(Context c,JSONObject t,String action) {
        String id=t.optString("id"); int revision=t.optInt("revision");
        Intent intent=new Intent(c,NotificationReceiver.class).setAction(action)
            .setData(Uri.parse("notenote://task/"+id+"/"+action+"/"+revision))
            .putExtra("taskId",id).putExtra("revision",revision);
        return PendingIntent.getBroadcast(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static PendingIntent open(Context c,JSONObject t,boolean reply) {
        String id=t.optString("id");
        Intent intent=new Intent(c,MainActivity.class).putExtra("taskId",id).putExtra("reply",reply)
            .setData(Uri.parse("notenote://task/"+id+(reply?"/reply":"")))
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    static String progressBody(JSONObject t) {
        JSONArray events=t.optJSONArray("events");
        if(events!=null) for(int i=events.length()-1;i>=0;i--) {
            JSONObject e=events.optJSONObject(i);
            if(e!=null&&e.optString("role").equals("assistant")&&!e.optString("summary").trim().isEmpty()) {
                String summary=e.optString("summary"); return summary.substring(0,Math.min(180,summary.length()));
            }
        }
        return "我往前推进了一步，来看看。";
    }
    static Notification build(Context c,JSONObject t,boolean progress) {
        String title=progress?(t.optBoolean("needsUser")?"想听听你的补充":"这条思考有了新进展"):"一件待办等你推进";
        String body=progress?progressBody(t):t.optString("text");
        Notification publicVersion=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Note Note").setContentText("有一条新的进展或提醒").build();
        Notification.Builder builder=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
            .setAutoCancel(true).setContentIntent(open(c,t,false));
        if(progress&&t.optString("kind").equals("think")&&!t.optBoolean("done"))
            builder.addAction(new Notification.Action.Builder(null,"接着聊",open(c,t,true)).build());
        return builder.addAction(new Notification.Action.Builder(null,"已完成",action(c,t,"complete")).build())
            .addAction(new Notification.Action.Builder(null,"明天提醒",action(c,t,"snooze")).build()).build();
    }
    static boolean send(Context c,JSONObject t,boolean progress) {
        if(!available(c)) return false;
        try { c.getSystemService(NotificationManager.class).notify(t.optString("id"),1,build(c,t,progress)); return true; }
        catch(SecurityException e) { return false; }
    }
    static void cancel(Context c,String id) { c.getSystemService(NotificationManager.class).cancel(id,1); }
    static int flush(Store store,boolean quiet,long now,Delivery delivery) throws Exception {
        if(quiet) return 0;
        int sent=0; JSONArray tasks=store.all();
        for(int i=0;i<tasks.length()&&sent<5;i++) {
            // Check state and acknowledge the exact event under the same lock as user edits.
            synchronized(store) {
                JSONObject t=store.find(tasks.getJSONObject(i).getString("id"));
                if(t==null||t.optBoolean("done")||t.optLong("snooze")>now) continue;
                String eventId=t.optString("pendingNotification");
                boolean progress=!eventId.isEmpty();
                boolean reminder=t.optString("kind").equals("action")&&Rules.reminderEligible(false,
                    t.optLong("due"),t.optLong("lastReminder"),t.optLong("snooze"),now);
                if((progress||reminder)&&delivery.send(t,progress)) {
                    if(progress) store.notificationSent(t.getString("id"),eventId);
                    if(reminder) store.reminderSent(t.getString("id"),now);
                    sent++;
                }
            }
        }
        return sent;
    }
}
