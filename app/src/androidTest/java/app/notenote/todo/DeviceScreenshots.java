package app.notenote.todo;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Screenshot evidence waits for pixels, not just a completed JavaScript call. */
final class DeviceScreenshots {
    static void capture(Context context,WebView web,String name) throws Exception {
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        CountDownLatch painted=new CountDownLatch(1);
        instrumentation.runOnMainSync(()->web.postVisualStateCallback(System.nanoTime(),new WebView.VisualStateCallback() {
            @Override public void onComplete(long requestId) {
                web.invalidate();
                web.postOnAnimation(()->web.postOnAnimation(painted::countDown));
            }
        }));
        assertTrue("WebView must finish painting before screenshot",painted.await(20,TimeUnit.SECONDS));
        instrumentation.waitForIdleSync();
        // The Android starting window may cover an already-painted WebView.
        // Reject blank frames until it is gone; persistent blank screens still fail.
        long deadline=android.os.SystemClock.elapsedRealtime()+5000;
        Bitmap bitmap=null;int dark=0;
        do {
            if(bitmap!=null)bitmap.recycle();
            bitmap=instrumentation.getUiAutomation().takeScreenshot();
            assertNotNull("Device screenshot must be available",bitmap);
            dark=darkSamples(bitmap);
            if(dark>200)break;
            android.os.SystemClock.sleep(100);
        } while(android.os.SystemClock.elapsedRealtime()<deadline);
        File dir=new File(context.getExternalFilesDir(null),"ui-evidence");
        assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
        } finally { bitmap.recycle(); }
        // UTP uninstalls app-scoped files after the run. Keep fixture screenshots for CI.
        shell("mkdir -p /sdcard/Download/NoteNoteEvidence");
        shell("cp "+new File(dir,name+".png").getAbsolutePath()+" /sdcard/Download/NoteNoteEvidence/"+name+".png");
        assertTrue("App screenshot is blank or has not painted (dark samples="+dark+")",dark>200);
    }
    private static int darkSamples(Bitmap bitmap) {
        int dark=0;
        // Exclude status/navigation bars so system UI cannot satisfy the check.
        for(int y=bitmap.getHeight()/6;y<bitmap.getHeight()*4/5;y+=4)
            for(int x=bitmap.getWidth()/12;x<bitmap.getWidth()*11/12;x+=4) {
                int pixel=bitmap.getPixel(x,y);
                if(android.graphics.Color.red(pixel)<170&&android.graphics.Color.green(pixel)<170&&android.graphics.Color.blue(pixel)<170)dark++;
            }
        return dark;
    }
    private static void shell(String command) throws Exception {
        ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
            byte[] buffer=new byte[1024];ByteArrayOutputStream output=new ByteArrayOutputStream();int count;
            while((count=in.read(buffer))!=-1)output.write(buffer,0,count);
            assertEquals("Screenshot copy must succeed","",output.toString("UTF-8").trim());
        }
    }
}
