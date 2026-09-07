package app.notenote.todo;
import org.junit.Test;
import static org.junit.Assert.*;
public class RulesTest {
 @Test public void manualKindWins(){assertEquals("note",Rules.classify("为什么模型这么训练", "note"));}
 @Test public void questionsTakePriority(){assertEquals("think",Rules.classify("为什么开会让我情绪低落", "auto"));}
 @Test public void physicalTasksAndPlainNotes(){assertEquals("action",Rules.classify("去办一张银行卡", "auto"));assertEquals("note",Rules.classify("雨后的空气很好", "auto"));}
 @Test public void resultWaitsForFeedback(){assertTrue(Rules.reviewEligible("think",false,0,0,100));assertFalse(Rules.reviewEligible("think",false,1,0,100));assertFalse(Rules.reviewEligible("think",true,0,0,100));assertFalse(Rules.reviewEligible("action",false,0,0,100));assertFalse(Rules.reviewEligible("think",false,0,200,100));}
 @Test public void quietWindowCrossesMidnight(){assertTrue(Rules.quiet(23,22,8));assertTrue(Rules.quiet(7,22,8));assertFalse(Rules.quiet(8,22,8));assertFalse(Rules.quiet(21,22,8));assertFalse(Rules.quiet(12,8,8));}
 @Test public void reminderDoesNotRepeatWithinDay(){assertTrue(Rules.reminderEligible(false,50,0,0,100));assertFalse(Rules.reminderEligible(false,50,60,0,100));assertFalse(Rules.reminderEligible(false,200,0,0,100));assertFalse(Rules.reminderEligible(true,50,0,0,100));}
 @Test public void endpointPreservesV1(){assertEquals("https://example.com/v1/chat/completions",Rules.endpoint("https://example.com/v1/"));assertEquals("https://example.com/v1/chat/completions",Rules.endpoint("https://example.com/v1/chat/completions"));}
 @Test public void endpointRejectsCredentialsAndCleartext(){for(String u:new String[]{"http://example.com/v1","https://key@example.com/v1","https://example.com/v1?key=secret","javascript:alert(1)"}){try{Rules.endpoint(u);fail(u);}catch(IllegalArgumentException expected){}}}
}
