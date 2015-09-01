/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jivesoftware.openfire.plugin.userService.roster;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonProperty;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Roster item for JSON transfer.
 * @author ph4r05
 */

@JsonAutoDetect
public class TransferRosterItem {
    @JsonProperty
    public String jid;
    
    @JsonProperty
    public String name;
    
    @JsonProperty
    public Integer subscription;
    
    @JsonProperty
    public Integer recvStatus;
    
    @JsonProperty
    public Integer askStatus;
    
    @JsonProperty
    public String groups;

    public static TransferRosterItem fromJSON(JSONObject obj) throws JSONException {
        final TransferRosterItem tri = new TransferRosterItem();
        tri.jid = obj.getString("jid");
        tri.name = obj.has("name") ? obj.getString("name") : null;
        tri.subscription = obj.has("subscription") ? obj.getInt("subscription") : null;
        tri.recvStatus = obj.has("recvStatus") ? obj.getInt("recvStatus") : null;
        tri.askStatus = obj.has("askStatus") ? obj.getInt("askStatus") : null;
        tri.groups = obj.has("groups") ? obj.getString("groups") : null;
        return tri;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TransferRosterItem that = (TransferRosterItem) o;

        if (askStatus != null ? !askStatus.equals(that.askStatus) : that.askStatus != null) return false;
        if (groups != null ? !groups.equals(that.groups) : that.groups != null) return false;
        if (jid != null ? !jid.equals(that.jid) : that.jid != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (recvStatus != null ? !recvStatus.equals(that.recvStatus) : that.recvStatus != null) return false;
        if (subscription != null ? !subscription.equals(that.subscription) : that.subscription != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = jid != null ? jid.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (subscription != null ? subscription.hashCode() : 0);
        result = 31 * result + (recvStatus != null ? recvStatus.hashCode() : 0);
        result = 31 * result + (askStatus != null ? askStatus.hashCode() : 0);
        result = 31 * result + (groups != null ? groups.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "TransferRosterItem{" + "jid=" + jid + ", name=" + name + ", subscription=" + subscription + ", recvStatus=" + recvStatus + ", askStatus=" + askStatus + ", groups=" + groups + '}';
    }
}
