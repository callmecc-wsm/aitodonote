package app.notenote.todo;
import android.app.Activity;
import android.content.Intent;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LaunchTest {
 @Test public void activityInstallsAndLaunches(){android.app.Instrumentation i=InstrumentationRegistry.getInstrumentation();Intent intent=new Intent(i.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);Activity a=i.startActivitySync(intent);assertNotNull(a);i.runOnMainSync(a::finish);}
}
