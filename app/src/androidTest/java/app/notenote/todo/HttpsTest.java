package app.notenote.todo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import javax.net.ssl.HttpsURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Real loopback HTTPS. No live model, credentials, or paid service is used. */
@RunWith(AndroidJUnit4.class)
public class HttpsTest {
    private MockWebServer server; private AiClient.Network network;
    @Before public void start() throws Exception {
        HeldCertificate cert=new HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();
        HandshakeCertificates serverTls=new HandshakeCertificates.Builder().heldCertificate(cert).build();
        HandshakeCertificates clientTls=new HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate()).build();
        server=new MockWebServer(); server.useHttps(serverTls.sslSocketFactory(),false); server.start();
        network=new AiClient.Network(url->{HttpsURLConnection c=(HttpsURLConnection)new URL(url).openConnection();
            c.setSSLSocketFactory(clientTls.sslSocketFactory()); return c;});
    }
    @After public void stop() throws Exception { server.shutdown(); }
    @Test public void sendsUtf8JsonOverTlsWithAuthorizationHeader() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ok\":true}"));
        assertTrue(network.post(server.url("/v1/chat/completions").toString(),"test-header-only",new JSONObject().put("text","中文记录")).getBoolean("ok"));
        RecordedRequest request=server.takeRequest(5,TimeUnit.SECONDS);
        assertNotNull(request); assertEquals("POST",request.getMethod());
        assertEquals("Bearer test-header-only",request.getHeader("Authorization"));
        assertEquals("中文记录",new JSONObject(request.getBody().readUtf8()).getString("text"));
    }
    @Test public void redirectsNeverForwardCredentials() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location",server.url("/redirected")));
        try { network.post(server.url("/start").toString(),"test-header-only",new JSONObject()); fail("redirect followed"); }
        catch(IllegalStateException e) { assertTrue(e.getMessage().contains("跳转")); }
        assertEquals(1,server.getRequestCount());
    }
    @Test public void providerErrorsDoNotExposeTheirResponseBody() throws Exception {
        for(int status:new int[]{401,403,404,429,500}) {
            server.enqueue(new MockResponse().setResponseCode(status).setBody("private-provider-payload"));
            try { network.post(server.url("/api").toString(),"test-header-only",new JSONObject()); fail("error accepted"); }
            catch(IllegalStateException e) { assertTrue(e.getMessage().contains("HTTP "+status)); assertFalse(e.getMessage().contains("private-provider-payload")); }
        }
    }
    @Test public void oversizedResponsesAreRejected() throws Exception {
        char[] chars=new char[2_000_100]; java.util.Arrays.fill(chars,'x');
        server.enqueue(new MockResponse().setBody(new String(chars)));
        try { network.post(server.url("/api").toString(),"test-header-only",new JSONObject()); fail("oversized response accepted"); }
        catch(IllegalStateException e) { assertTrue(e.getMessage().contains("过大")); }
    }
}
