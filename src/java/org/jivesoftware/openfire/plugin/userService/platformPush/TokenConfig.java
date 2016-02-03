package org.jivesoftware.openfire.plugin.userService.platformPush;

import org.jivesoftware.openfire.plugin.userService.utils.MiscUtils;
import org.jivesoftware.util.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmpp.packet.JID;

import java.sql.ResultSet;
import java.util.ArrayList;

/**
 *
 * Represents token configuration built from the push message.
 *
 * {"platform":"ios", "token":"0f34819381000405",
 *  "version":"1", "app_version":"1.2.0", "os_version":"8.4",
 *  "langs":["cs", "en"],
 *  "debug":1
 * }
 *
 * Created by dusanklinec on 03.07.15.
 */
public class TokenConfig {
    public static final String PLATFORM_IOS = "ios";
    public static final String PLATFORM_ANDROID = "android";

    final static String FIELD_PLATFORM   = "platform";
    final static String FIELD_TOKEN      = "token";
    final static String FIELD_FAKE_UDID  = "fudid";
    final static String FIELD_VERSION    = "version";
    final static String FIELD_OSVERSION  = "os_version";
    final static String FIELD_APPVERSION = "app_version";
    final static String FIELD_LANGS      = "langs";
    final static String FIELD_DEBUG      = "debug";

    /**
     * user/resource for this token. Primary key.
     */
    protected JID user;

    /**
     * Required element, platform for which push messages are configured.
     */
    protected String platform;

    protected String token;
    protected String fakeUdid;
    protected String version;
    protected String appVersion;
    protected String osVersion;
    protected ArrayList<String> langs = new ArrayList<String>();
    protected Boolean debug;
    protected String auxJson;

    /**
     * Creates token configuration from JSON packet.
     * @param json
     * @return
     */
    public static TokenConfig buildFromJson(JSONObject json) throws JSONException {
        final TokenConfig toRet = new TokenConfig();

        toRet.platform = json.getString(FIELD_PLATFORM);
        if (toRet.platform == null || toRet.platform.isEmpty()){
            return null;
        }

        final String tmpPlatform = toRet.platform.trim();
        if (PLATFORM_IOS.equalsIgnoreCase(tmpPlatform)){
            toRet.platform = PLATFORM_IOS;
        } else if (PLATFORM_ANDROID.equalsIgnoreCase(tmpPlatform)){
            toRet.platform = PLATFORM_ANDROID;
        } else {
            // Legacy reasons, default platform is ios. Deprecated.
            toRet.platform = PLATFORM_IOS;
        }

        toRet.token      = json.getString(FIELD_TOKEN);
        toRet.version    = json.getString(FIELD_VERSION);
        toRet.appVersion = json.has(FIELD_APPVERSION) ? json.getString(FIELD_APPVERSION) : "";
        toRet.osVersion  = json.has(FIELD_OSVERSION)  ? json.getString(FIELD_OSVERSION)  : "";
        toRet.fakeUdid   = json.has(FIELD_FAKE_UDID)  ? json.getString(FIELD_FAKE_UDID)  : "";

        // Extract array of languages.
        if (json.has(FIELD_LANGS)){
            final JSONArray langArr = json.getJSONArray(FIELD_LANGS);
            final int langsNum = langArr.length();
            for(int i=0; i<langsNum; i++){
                toRet.langs.add(langArr.getString(i));
            }
        }

        if (json.has(FIELD_DEBUG)){
            toRet.debug = MiscUtils.getAsBoolean(json, FIELD_DEBUG);
        }

        return toRet;
    }

    public String getLangList(){
        if (langs == null || langs.isEmpty()){
            return "";
        }

        final int cn = langs.size();
        final StringBuilder sb = new StringBuilder();
        for(int i=0; i<cn; i++){
            sb.append(langs.get(i));
            if ((i+1) < cn){
                sb.append(",");
            }
        }

        return sb.toString();
    }

    /**
     * Initializes language list from the comma separated language list.
     * @param langsLst
     */
    public void setLangsField(String langsLst) {
        if (langsLst == null || langsLst.isEmpty()) {
            return;
        }

        langs.clear();
        final String[] parts = langsLst.split(",");
        for(String part : parts){
            if (part.isEmpty()){
                continue;
            }

            langs.add(part.trim());
        }
    }

    @Override
    public String toString() {
        return "TokenConfig{" +
                "user=" + user +
                ", platform='" + platform + '\'' +
                ", token='" + token + '\'' +
                ", fakeUdid='" + fakeUdid + '\'' +
                ", version='" + version + '\'' +
                ", appVersion='" + appVersion + '\'' +
                ", osVersion='" + osVersion + '\'' +
                ", langs=" + langs +
                ", debug=" + debug +
                ", auxJson='" + auxJson + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TokenConfig that = (TokenConfig) o;

        if (user != null ? !user.equals(that.user) : that.user != null) return false;
        if (platform != null ? !platform.equals(that.platform) : that.platform != null) return false;
        if (token != null ? !token.equals(that.token) : that.token != null) return false;
        if (version != null ? !version.equals(that.version) : that.version != null) return false;
        if (appVersion != null ? !appVersion.equals(that.appVersion) : that.appVersion != null) return false;
        if (osVersion != null ? !osVersion.equals(that.osVersion) : that.osVersion != null) return false;
        if (langs != null ? !langs.equals(that.langs) : that.langs != null) return false;
        return !(debug != null ? !debug.equals(that.debug) : that.debug != null);

    }

    @Override
    public int hashCode() {
        int result = user != null ? user.hashCode() : 0;
        result = 31 * result + (platform != null ? platform.hashCode() : 0);
        result = 31 * result + (token != null ? token.hashCode() : 0);
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (appVersion != null ? appVersion.hashCode() : 0);
        result = 31 * result + (osVersion != null ? osVersion.hashCode() : 0);
        result = 31 * result + (langs != null ? langs.hashCode() : 0);
        result = 31 * result + (debug != null ? debug.hashCode() : 0);
        return result;
    }

    public boolean isIos(){
        if (platform==null || platform.isEmpty()){
            platform = PLATFORM_IOS;
        }

        return PLATFORM_IOS.equals(platform);
    }

    public boolean isAndroid(){
        return PLATFORM_ANDROID.equals(platform);
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getOsVersion() {
        return osVersion;
    }

    public void setOsVersion(String osVersion) {
        this.osVersion = osVersion;
    }

    public ArrayList<String> getLangs() {
        return langs;
    }

    public void setLangs(ArrayList<String> langs) {
        this.langs = langs;
    }

    public Boolean getDebug() {
        return debug;
    }

    public void setDebug(Boolean debug) {
        this.debug = debug;
    }

    public JID getUser() {
        return user;
    }

    public void setUser(JID user) {
        this.user = user;
    }

    public String getAuxJson() {
        return auxJson;
    }

    public void setAuxJson(String auxJson) {
        this.auxJson = auxJson;
    }

    public String getFakeUdid() {
        return fakeUdid;
    }

    public void setFakeUdid(String fakeUdid) {
        this.fakeUdid = fakeUdid;
    }
}
