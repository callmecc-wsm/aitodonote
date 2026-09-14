package app.notenote.todo;

import org.json.JSONArray;
import org.json.JSONObject;

/** Device-independent orchestration with injectable I/O; all production writes still go through SQLite. */
final class ReviewEngine {
    interface Reviewer { JSONObject review(JSONObject task) throws Exception; }
    interface Stop { boolean stopped(); }
    interface Progress { void active(String id); }
    static final class Report {
        int attempted,completed,failed,discarded;
        String error="";
    }
    static boolean eligible(JSONObject t,long now) {
        return Rules.reviewEligible(t.optString("kind"),t.optBoolean("done"),t.optLong("lastReview"),
            t.optLong("snooze"),t.optLong("nextReview"),t.optBoolean("needsUser"),
            t.optInt("rounds"),t.optString("error"),now);
    }
    static Report run(Store store,Reviewer reviewer,Stop stop,Progress progress,String requested,long now) throws Exception {
        Report report=new Report(); JSONArray all=store.all();
        for(int i=all.length()-1;i>=0&&report.attempted<3;i--) {
            if(stop.stopped()) break;
            String id=all.getJSONObject(i).getString("id");
            if(requested!=null&&!requested.isEmpty()&&!requested.equals(id)) continue;
            JSONObject t=store.find(id);
            if(t==null||!eligible(t,now)) continue;
            progress.active(id); report.attempted++;
            try {
                JSONObject event=reviewer.review(t);
                if(stop.stopped()) { report.discarded++; break; }
                if(store.resultAt(id,t.optInt("revision"),event,"",now)) report.completed++;
                else report.discarded++;
            } catch(Exception e) {
                if(stop.stopped()) { report.discarded++; break; }
                // Low-level argument errors can echo HTTP header values; never persist them.
                report.error=e instanceof IllegalStateException
                    ?e.getMessage():"连接失败，请检查网络后重试";
                if(report.error==null) report.error="本轮连接失败";
                if(report.error.length()>500) report.error=report.error.substring(0,500);
                store.resultAt(id,t.optInt("revision"),null,report.error,now); report.failed++;
                break; // A provider failure pauses the whole pass; never charge other notes blindly.
            }
        }
        progress.active(""); return report;
    }
}
