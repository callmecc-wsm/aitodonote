package app.notenote.todo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReviewEngineTest extends DeviceTestBase {
    @Test public void researchContinuesAfterADayAndStopsAtThreeRounds() throws Exception {
        JSONObject t=note("模型怎样学习","think"); String id=t.getString("id");
        long now=System.currentTimeMillis();
        assertEquals(1,run(x->event("research",""),now).completed);
        assertEquals(0,run(x->{fail("too early");return null;},now+Rules.DAY-1).attempted);
        assertEquals(1,run(x->event("research",""),now+Rules.DAY).completed);
        assertEquals(1,run(x->event("research",""),now+2*Rules.DAY).completed);
        assertEquals(0,run(x->{fail("fourth automatic round");return null;},now+3*Rules.DAY).attempted);
        assertEquals(3,store.find(id).getInt("rounds"));
        assertEquals(0,store.find(id).getLong("nextReview"));
        assertFalse(store.find(id).getBoolean("done"));
        assertEquals("模型怎样学习",store.find(id).getString("text"));
    }
    @Test public void questionWaitsForUserAndReplyRestartsCycle() throws Exception {
        String id=note("不知道如何处理工作问题","think").getString("id"); long now=System.currentTimeMillis();
        run(x->event("research","你最担心哪一部分？"),now);
        assertTrue(store.find(id).getBoolean("needsUser"));
        assertEquals(0,run(x->{fail("must wait for reply");return null;},now+7*Rules.DAY).attempted);
        store.change(id,"reply","担心沟通不清楚");
        assertEquals(1,run(x->event("enough",""),now+7*Rules.DAY).completed);
        assertEquals(1,store.find(id).getInt("rounds"));
    }
    @Test public void physicalNotesCompletedAndSnoozedAreNeverSent() throws Exception {
        note("去办银行卡","action"); note("今天的记录","note");
        String done=note("已完成的问题","think").getString("id"); store.change(done,"done","");
        String snooze=note("明天想的问题","think").getString("id"); store.change(snooze,"snooze","");
        assertEquals(0,run(x->{fail("ineligible note sent");return null;},System.currentTimeMillis()).attempted);
    }
    @Test public void staleResultsStillConsumeBatchBudget() throws Exception {
        for(int n=0;n<4;n++) note("问题 "+n,"think");
        ReviewEngine.Report report=run(t->{
            store.save(new JSONObject().put("id",t.getString("id")).put("text","已修改 "+t.getString("text")).put("kind","think"));
            return event("enough","");
        },System.currentTimeMillis());
        assertEquals(3,report.attempted); assertEquals(3,report.discarded); assertEquals(0,report.completed);
        for(int i=0;i<store.all().length();i++) assertEquals(0,store.all().getJSONObject(i).getJSONArray("events").length());
    }
    @Test public void cancellationDoesNotSaveTheInflightResult() throws Exception {
        String id=note("问题","think").getString("id"); AtomicBoolean stopped=new AtomicBoolean();
        ReviewEngine.Report report=ReviewEngine.run(store,t->{stopped.set(true);return event("enough","");},
            stopped::get,x->{},"",System.currentTimeMillis());
        assertEquals(1,report.discarded); assertEquals(0,store.find(id).getJSONArray("events").length());
        assertTrue(ReviewEngine.eligible(store.find(id),System.currentTimeMillis()));
    }
    @Test public void selectionRefreshesEachTaskAfterPreviousRequest() throws Exception {
        note("第一条问题","think"); String second=note("第二条问题","think").getString("id");
        assertEquals(1,run(t->{store.change(second,"complete","");return event("enough","");},System.currentTimeMillis()).attempted);
    }
    @Test public void providerFailureStopsBatchAndRequiresRetry() throws Exception {
        String first=note("第一条","think").getString("id"); note("第二条","think");
        ReviewEngine.Report report=run(t->{throw new IllegalStateException("鉴权失败");},System.currentTimeMillis());
        assertEquals(1,report.attempted); assertEquals(1,report.failed);
        assertEquals("鉴权失败",store.find(first).getString("error"));
        assertFalse(ReviewEngine.eligible(store.find(first),System.currentTimeMillis()+Rules.DAY));
        store.clearErrors(); assertTrue(ReviewEngine.eligible(store.find(first),System.currentTimeMillis()));
    }
    @Test public void transportArgumentErrorsCannotLeakHeadersIntoBackups() throws Exception {
        String id=note("需要研究的问题","think").getString("id");
        ReviewEngine.Report report=run(t->{throw new IllegalArgumentException("Invalid Authorization: Bearer private-test-canary");},System.currentTimeMillis());
        assertEquals(1,report.failed);
        assertFalse(report.error.contains("private-test-canary"));
        assertFalse(store.find(id).getString("error").contains("private-test-canary"));
        assertFalse(store.backup().toString().contains("private-test-canary"));
    }
    @Test public void targetedReviewDoesNotSendOtherNotes() throws Exception {
        note("第一条","think"); String id=note("指定问题","think").getString("id");
        ReviewEngine.Report report=ReviewEngine.run(store,t->{assertEquals(id,t.getString("id"));return event("enough","");},
            ()->false,x->{},id,System.currentTimeMillis());
        assertEquals(1,report.attempted);
    }
}
