/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jivesoftware.openfire.plugin;

import java.util.Objects;
import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonProperty;

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

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 17 * hash + Objects.hashCode(this.jid);
        hash = 17 * hash + Objects.hashCode(this.name);
        hash = 17 * hash + Objects.hashCode(this.subscription);
        hash = 17 * hash + Objects.hashCode(this.recvStatus);
        hash = 17 * hash + Objects.hashCode(this.askStatus);
        hash = 17 * hash + Objects.hashCode(this.groups);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TransferRosterItem other = (TransferRosterItem) obj;
        if (!Objects.equals(this.jid, other.jid)) {
            return false;
        }
        if (!Objects.equals(this.name, other.name)) {
            return false;
        }
        if (!Objects.equals(this.subscription, other.subscription)) {
            return false;
        }
        if (!Objects.equals(this.recvStatus, other.recvStatus)) {
            return false;
        }
        if (!Objects.equals(this.askStatus, other.askStatus)) {
            return false;
        }
        if (!Objects.equals(this.groups, other.groups)) {
            return false;
        }
        return true;
    }
    
    @Override
    public String toString() {
        return "TransferRosterItem{" + "jid=" + jid + ", name=" + name + ", subscription=" + subscription + ", recvStatus=" + recvStatus + ", askStatus=" + askStatus + ", groups=" + groups + '}';
    }
}
