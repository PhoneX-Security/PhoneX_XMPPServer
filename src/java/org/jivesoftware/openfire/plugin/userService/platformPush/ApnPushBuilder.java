package org.jivesoftware.openfire.plugin.userService.platformPush;

import com.google.api.client.json.Json;
import com.notnoop.apns.APNS;
import org.jivesoftware.openfire.plugin.userService.db.DbPlatformPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessage;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.ApnMessageBuilder;
import org.jivesoftware.openfire.plugin.userService.platformPush.apnMessage.NewMissedCallMsg;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.NewActiveCallPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.NewMessagePush;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.NewMissedCallPush;
import org.jivesoftware.openfire.plugin.userService.platformPush.reqMessage.PushRequestMessage;
import org.jivesoftware.openfire.plugin.userService.push.PushMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     */
    private static final String[] alertMap = {
            /* 0 */ null,
            /* 1 */ "L_PHX_PUSH_ALERT_MSG",
            /* 2 */ "L_PHX_PUSH_ALERT_MCALL",
            /* 3 */ "L_PHX_PUSH_ALERT_MCALL_MSG",
            /* 4 */ "L_PHX_PUSH_ALERT_ACALL",
            /* 5 */ "L_PHX_PUSH_ALERT_ACALL_MSG",
            /* 6 */ "L_PHX_PUSH_ALERT_ACALL_MCALL",
            /* 7 */ "L_PHX_PUSH_ALERT_ACALL_MCALL_MSG",
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
    protected String alertString;

    protected JSONObject jsonPayload;
    protected String payload;

    protected String phxPushPayload;
    protected JSONObject jsonPhxPushPayload;

    /**
     * Grouping, processing of the input messages.
     */
    protected void preprocess(){
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
                    topAlert = msg.getAlertString();
                    totalUrgency = curUrg;
                }
            }
        }
    }

    /**
     * Build apple push.
     */
    public void build() throws JSONException {
        // Group, categorize, compute priorities.
        preprocess();

        // Generate total alert name.
        boolean newMsg    = actionPush.containsKey(NewMissedCallPush.ACTION);
        boolean newMissed = actionPush.containsKey(NewMessagePush.ACTION);
        boolean newCall   = actionPush.containsKey(NewActiveCallPush.ACTION);
        int alertIdx = newMsg    ? 1 : 0;
        alertIdx    |= newMissed ? 1 << 1: 0;
        alertIdx    |= newCall   ? 1 << 2: 0;
        alertString = alertMap[alertIdx];

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
                .localizedKey(alertString)
                .actionKey("Show")
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

    public String getAlertString() {
        return alertString;
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
}