package app.notenote.todo;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class Config {
    final SharedPreferences prefs;
    private static final String ALIAS="notenote-device-key";
    public Config(Context c) { prefs=c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
    private synchronized SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator gen=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            gen.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    public String secret(String name) throws Exception {
        String data=prefs.getString(name,""); if (data.isEmpty()) return "";
        String[] parts=data.split(":",2);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
    }
    private String encrypt(String value) throws Exception {
        if(value.isEmpty()) return "";
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
        return Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP);
    }
    public boolean ready() { return !prefs.getString("apiKey","").isEmpty() && !prefs.getString("baseUrl","").isEmpty() && !prefs.getString("model","").isEmpty(); }
    public JSONObject publicState() throws Exception {
        return new JSONObject().put("baseUrl",prefs.getString("baseUrl",""))
            .put("model",prefs.getString("model",""))
            .put("hasKey",!prefs.getString("apiKey","").isEmpty())
            .put("hasSearchKey",!prefs.getString("searchKey","").isEmpty())
            .put("search",prefs.getBoolean("search",false)).put("enabled",prefs.getBoolean("enabled",false))
            .put("interval",prefs.getInt("interval",6)).put("quietFrom",prefs.getInt("quietFrom",22))
            .put("quietTo",prefs.getInt("quietTo",8)).put("lastRun",prefs.getLong("lastRun",0))
            .put("lastStatus",prefs.getString("lastStatus","还没有回顾记录")).put("ready",ready());
    }
    public synchronized void save(JSONObject input) throws Exception {
        String url=input.optString("baseUrl").trim(), model=input.optString("model").trim();
        if (!url.isEmpty()) Rules.endpoint(url);
        int interval=input.optInt("interval",6);
        if (interval!=1 && interval!=3 && interval!=6 && interval!=12 && interval!=24) throw new IllegalArgumentException("回顾周期不正确");
        int from=input.optInt("quietFrom",22),to=input.optInt("quietTo",8);
        if(from<0||from>23||to<0||to>23) throw new IllegalArgumentException("安静时段不正确");
        String newApi=input.optString("apiKey").trim(),newSearch=input.optString("searchKey").trim();
        boolean clear=input.optBoolean("clearKey"),clearSearch=input.optBoolean("clearSearchKey");
        boolean hasApi=!clear && (!newApi.isEmpty() || !prefs.getString("apiKey","").isEmpty());
        boolean hasSearch=!clearSearch && (!newSearch.isEmpty() || !prefs.getString("searchKey","").isEmpty());
        if(input.optBoolean("enabled") && (!hasApi||url.isEmpty()||model.isEmpty())) throw new IllegalArgumentException("先填写模型地址、模型名称和 API Key");
        if(input.optBoolean("search") && !hasSearch) throw new IllegalArgumentException("联网检索需要 Tavily API Key");
        SharedPreferences.Editor e=prefs.edit().putString("baseUrl",url).putString("model",model)
            .putBoolean("enabled",input.optBoolean("enabled")).putBoolean("search",input.optBoolean("search"))
            .putInt("interval",interval).putInt("quietFrom",from).putInt("quietTo",to);
        if(clear) e.remove("apiKey"); else if(!newApi.isEmpty()) e.putString("apiKey",encrypt(newApi));
        if(clearSearch) e.remove("searchKey"); else if(!newSearch.isEmpty()) e.putString("searchKey",encrypt(newSearch));
        if(!e.commit()) throw new IllegalStateException("设置保存失败");
    }
}
