package org.jivesoftware.openfire.plugin.userService.platformPush;

import com.notnoop.apns.APNS;
import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.db.DbStrings;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessage;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBuilder;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewMissedCallOfflineMsg;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.*;
import org.jivesoftware.openfire.plugin.userService.strings.StringsManager;
import org.jivesoftware.openfire.plugin.userService.utils.MiscUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Builder for payload building for the Apple push notifications.
 *
 * Created by dusanklinec on 17.07.15.
 */
public class ApnPushBuilder {
    private static final Logger log = LoggerFactory.getLogger(ApnPushBuilder.class);

    /**
     * Alert formatting string map.
     *   1 .. new message.
     *   2 .. new missed call.
     *   4 .. new active call.
     *   8 .. new event
     *   16 . new attention
     */
    private static final String[] ALERT_KEY_MAP = {
            /* 0 */ null,
            /* 1 */ "L_PHX_PUSH_ALERT_MSG",
            /* 2 */ "L_PHX_PUSH_ALERT_MCALL",
            /* 3 */ "L_PHX_PUSH_ALERT_MCALL_MSG",
            /* 4 */ "L_PHX_PUSH_ALERT_ACALL",
            /* 5 */ "L_PHX_PUSH_ALERT_ACALL_MSG",
            /* 6 */ "L_PHX_PUSH_ALERT_ACALL_MCALL",
            /* 7 */ "L_PHX_PUSH_ALERT_ACALL_MCALL_MSG",
            /* 8 */ "L_PHX_PUSH_ALERT_EVT",
            /* 9 */ "L_PHX_PUSH_ALERT_MSG_EVT",
            /*10 */ "L_PHX_PUSH_ALERT_MCALL_EVT",
            /*11 */ "L_PHX_PUSH_ALERT_MCALL_MSG_EVT",
            /*12 */ "L_PHX_PUSH_ALERT_EVT",
            /*13 */ "L_PHX_PUSH_ALERT_EVT",
            /*14 */ "L_PHX_PUSH_ALERT_EVT",
            /*15 */ "L_PHX_PUSH_ALERT_EVT",
            /*16 */ "L_PHX_PUSH_ALERT_ATT",
    };

    protected String user;
    protected List<TokenConfig> tokens;
    protected List<DbPlatformPush> pushMessagesList;

    // Group messages by action.
    protected final Map<String, List<DbPlatformPush>> actionMap = new HashMap<String, List<DbPlatformPush>>();
    // Mapping Action -> Number of notifications in this action group.
    protected final Map<String, Integer> actionBadge = new HashMap<String, Integer>();
    // Mapping Action -> most recent notification (its timestamp) for this action.
    protected final Map<String, Long> actionTime = new HashMap<String, Long>();
    // Mapping Action -> the most recent notification (by its timestamp).
    protected final Map<String, DbPlatformPush> actionPush = new HashMap<String, DbPlatformPush>();

    protected String topAlert = null;
    protected int totalUrgency = PushRequestMessage.URGENCY_MIN;
    protected int totalBadge = 0;
    protected String alertStringKey;
    protected String alertString;

    protected JSONObject jsonPayload;
    protected String payload;

    protected String phxPushPayload;
    protected JSONObject jsonPhxPushPayload;

    protected TokenConfig currentToken;
    protected StringsManager strings;

    /**
     * Grouping, processing of the input messages.
     */
    protected void preprocess(){
        // Reset old badges count.
        totalBadge = 0;
        actionBadge.clear();
        for(DbPlatformPush ppush : pushMessagesList){
            final String action = ppush.getAction();
            List<DbPlatformPush> lstToUse = null;

            if (!actionMap.containsKey(action)){
                lstToUse = new ArrayList<DbPlatformPush>();
                actionMap.put(action, lstToUse);

            } else {
                lstToUse = actionMap.get(action);

            }

            lstToUse.add(ppush);

            // Compute badge number for given action.
            Integer curNum = actionBadge.get(action);
            actionBadge.put(action, curNum == null ? 1 : curNum + 1);
            totalBadge += 1;

            // Most recent timestamp for given action.
            Long lastTstamp = actionTime.get(action);
            if (lastTstamp == null || lastTstamp < ppush.getTime()){
                actionTime.put(action, ppush.getTime());
                actionPush.put(action, ppush);
            }

            // Overall urgency, maximum number.
            final PushRequestMessage msg = PushParser.getMessageByAction(action, false);
            if (msg != null){
                final int curUrg = msg.getUrgencyType();
                if (curUrg > totalUrgency){
                    topAlert = msg.getAlertStringKey();
                    totalUrgency = curUrg;
                }
            }
        }
    }

    /**
     * Attempts to construct alert string key from all messages in the current push bulk.
     */
    public void buildAlertStringKey(){
        boolean newMsg    = actionPush.containsKey(NewMessagePush.ACTION);
        boolean newMissed = actionPush.containsKey(NewMissedCallPush.ACTION);
        boolean newCall   = actionPush.containsKey(NewActiveCallPush.ACTION);
        boolean newEvent  = actionPush.containsKey(NewEventPush.ACTION);
        boolean newAttention = actionPush.containsKey(NewAttentionPush.ACTION);
        boolean newOffline = actionPush.containsKey(NewOfflineMsgPush.ACTION);

        // Offline events.
        newMissed |= actionPush.containsKey(NewMissedCallOfflinePush.ACTION);
        newMsg    |= actionPush.containsKey(NewMessageOfflinePush.ACTION);

        int alertIdx = newMsg       ? 1 : 0;
        alertIdx    |= newMissed    ? 1 << 1: 0;
        alertIdx    |= newCall      ? 1 << 2: 0;
        alertIdx    |= newEvent     ? 1 << 3: 0;
        alertIdx    |= newAttention ? 1 << 4: 0;
        alertIdx    |= newOffline   ? 1 << 5: 0;

        if (alertIdx >= ALERT_KEY_MAP.length){
            try {
                final int highestBit = MiscUtils.lg2(alertIdx);
                if (highestBit < ALERT_KEY_MAP.length) {
                    alertStringKey = ALERT_KEY_MAP[highestBit];
                }

            } catch(Exception e){
                log.error("Exception in alert string building", e);
            }

        } else {
            alertStringKey = ALERT_KEY_MAP[alertIdx];

        }
    }

    /**
     * BBuilds string description
     */
    public void buildAlertString(){
        // TODO: implement title from the message content (missed call, new message, ...?)
        alertString = "New activity on PhoneX";
        if (currentToken == null || strings == null){
            return;
        }

        final List<Locale> locales = StringsManager.getLocales(currentToken.getLangs());
        final DbStrings newActivityString = strings.loadStringCached("PUSH_NEW_ACTIVITY", locales);
        if (newActivityString == null){
            return;
        }

        alertString = newActivityString.getValue();
    }

    public void build() throws JSONException {
        buildForToken(null);
    }

    /**
     * Build apple push.
     */
    public void buildForToken(TokenConfig token) throws JSONException {
        currentToken = token;

        // Group, categorize, compute priorities.
        preprocess();

        // Generate total alert name.
        buildAlertStringKey();
        buildAlertString();

        // Build custom JSON notification.
        JSONObject root = new JSONObject();
        JSONArray pushArr = new JSONArray();
        for (Map.Entry<String, DbPlatformPush> eset : actionPush.entrySet()) {
            final String action = eset.getKey();
            final DbPlatformPush nPush = eset.getValue();
            final ApnMessage apnMessage = ApnMessageBuilder.buildMessageFromDb(nPush, true);
            if (apnMessage == null){
                continue;
            }

            // Set badge number for the notification - how many of them.
            apnMessage.setBadge(actionBadge.get(action));
            final JSONObject json = apnMessage.apnToJson();
            pushArr.put(json);
        }

        root.put("msg", pushArr);
        jsonPhxPushPayload = root;
        phxPushPayload = root.toString();

        // Build general APN payload.
        final String tmpPayload = APNS.newPayload()
                .badge(totalBadge)
                .alertBody(alertString)
                .alertTitle(alertString)
                .build();

        // Parse general payload, build JSON.
        jsonPayload = new JSONObject(tmpPayload);
        jsonPayload.put("phx", root);
        payload = jsonPayload.toString();
    }

    public String getUser() {
        return user;
    }

    public ApnPushBuilder setUser(String user) {
        this.user = user;
        return this;
    }

    public List<TokenConfig> getTokens() {
        return tokens;
    }

    public ApnPushBuilder setTokens(List<TokenConfig> tokens) {
        this.tokens = tokens;
        return this;
    }

    public List<DbPlatformPush> getPushMessagesList() {
        return pushMessagesList;
    }

    public ApnPushBuilder setPushMessagesList(List<DbPlatformPush> pushMessagesList) {
        this.pushMessagesList = pushMessagesList;
        return this;
    }

    public String getAlertStringKey() {
        return alertStringKey;
    }

    public int getTotalBadge() {
        return totalBadge;
    }

    public int getTotalUrgency() {
        return totalUrgency;
    }

    public String getTopAlert() {
        return topAlert;
    }

    public JSONObject getJsonPayload() {
        return jsonPayload;
    }

    public String getPayload() {
        return payload;
    }

    public String getPhxPushPayload() {
        return phxPushPayload;
    }

    public JSONObject getJsonPhxPushPayload() {
        return jsonPhxPushPayload;
    }

    public StringsManager getStrings() {
        return strings;
    }

    public ApnPushBuilder setStrings(StringsManager strings) {
        this.strings = strings;
        return this;
    }
}
