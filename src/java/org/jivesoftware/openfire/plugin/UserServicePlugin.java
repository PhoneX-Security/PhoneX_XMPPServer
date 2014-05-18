/**
 * $Revision: 1722 $
 * $Date: 2005-07-28 15:19:16 -0700 (Thu, 28 Jul 2005) $
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.group.Group;
import org.jivesoftware.openfire.group.GroupManager;
import org.jivesoftware.openfire.group.GroupNotFoundException;
import org.jivesoftware.openfire.group.GroupAlreadyExistsException;
import org.jivesoftware.openfire.lockout.LockOutManager;
import org.jivesoftware.openfire.roster.Roster;
import org.jivesoftware.openfire.roster.RosterItem;
import org.jivesoftware.openfire.roster.RosterManager;
import org.jivesoftware.openfire.user.User;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;
import org.xmpp.packet.JID;

/**
 * Plugin that allows the administration of users via HTTP requests.
 *
 * @author Justin Hunt
 */
public class UserServicePlugin implements Plugin, PropertyEventListener {
    private UserManager userManager;
    private RosterManager rosterManager;
    private XMPPServer server;

    private String secret;
    private boolean enabled;
    private Collection<String> allowedIPs;

    public void initializePlugin(PluginManager manager, File pluginDirectory) {
        server = XMPPServer.getInstance();
        userManager = server.getUserManager();
        rosterManager = server.getRosterManager();

        secret = JiveGlobals.getProperty("plugin.userservice.secret", "");
        // If no secret key has been assigned to the user service yet, assign a random one.
        if (secret.equals("")){
            secret = StringUtils.randomString(8);
            setSecret(secret);
        }

        // See if the service is enabled or not.
        enabled = JiveGlobals.getBooleanProperty("plugin.userservice.enabled", false);

        // Get the list of IP addresses that can use this service. An empty list means that this filter is disabled.
        allowedIPs = StringUtils.stringToCollection(JiveGlobals.getProperty("plugin.userservice.allowedIPs", ""));

        // Listen to system property events
        PropertyEventDispatcher.addListener(this);
    }

    public void destroyPlugin() {
        userManager = null;
        // Stop listening to system property events
        PropertyEventDispatcher.removeListener(this);
    }

    public void createUser(String username, String password, String name, String email, String groupNames)
            throws UserAlreadyExistsException, GroupAlreadyExistsException, UserNotFoundException, GroupNotFoundException
    {
        userManager.createUser(username, password, name, email);
        userManager.getUser(username);

        if (groupNames != null) {
            Collection<Group> groups = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");

            while (tkn.hasMoreTokens())
            {
				String groupName = tkn.nextToken();
				Group group = null;

                try {
                    GroupManager.getInstance().getGroup(groupName);
                } catch (GroupNotFoundException e) {
                    // Create this group                    ;
					GroupManager.getInstance().createGroup(groupName);
                }
				group = GroupManager.getInstance().getGroup(groupName);
				group.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
				group.getProperties().put("sharedRoster.displayName", groupName);
				group.getProperties().put("sharedRoster.groupList", "");

                groups.add(group);
            }
            for (Group group : groups) {
                group.getMembers().add(server.createJID(username, null));
            }
        }
    }

    public void deleteUser(String username) throws UserNotFoundException, SharedGroupException
    {
        User user = getUser(username);
        userManager.deleteUser(user);

		rosterManager.deleteRoster(server.createJID(username, null));
    }

    /**
     * Lock Out on a given username
     *
     * @param username the username of the local user to disable.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    public void disableUser(String username) throws UserNotFoundException
    {
        User user = getUser(username);
        LockOutManager.getInstance().disableAccount(username, null, null);
    }

    /**
     * Remove the lockout on a given username
     *
     * @param username the username of the local user to enable.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    public void enableUser(String username) throws UserNotFoundException
    {
        User user = getUser(username);
        LockOutManager.getInstance().enableAccount(username);
    }

    public void updateUser(String username, String password, String name, String email, String groupNames)
            throws UserNotFoundException, GroupAlreadyExistsException
    {
        User user = getUser(username);
        if (password != null) user.setPassword(password);
        if (name != null) user.setName(name);
        if (email != null) user.setEmail(email);

        if (groupNames != null) {
            Collection<Group> newGroups = new ArrayList<Group>();
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");

            while (tkn.hasMoreTokens())
            {
				String groupName = tkn.nextToken();
				Group group = null;

                try {
                    group = GroupManager.getInstance().getGroup(groupName);
                } catch (GroupNotFoundException e) {
                    // Create this group                    ;
					group = GroupManager.getInstance().createGroup(groupName);
                	group.getProperties().put("sharedRoster.showInRoster", "onlyGroup");
                	group.getProperties().put("sharedRoster.displayName", groupName);
                	group.getProperties().put("sharedRoster.groupList", "");
                }

                newGroups.add(group);
            }

            Collection<Group> existingGroups = GroupManager.getInstance().getGroups(user);
            // Get the list of groups to add to the user
            Collection<Group> groupsToAdd =  new ArrayList<Group>(newGroups);
            groupsToAdd.removeAll(existingGroups);
            // Get the list of groups to remove from the user
            Collection<Group> groupsToDelete =  new ArrayList<Group>(existingGroups);
            groupsToDelete.removeAll(newGroups);

            // Add the user to the new groups
            for (Group group : groupsToAdd) {
                group.getMembers().add(server.createJID(username, null));
            }
            // Remove the user from the old groups
            for (Group group : groupsToDelete) {
                group.getMembers().remove(server.createJID(username, null));
            }
        }
    }
    
    /**
     * Sync the roster with one specified in this request.
     * @param username
     * @param rosterList 
     * @throws org.jivesoftware.openfire.user.UserNotFoundException 
     * @throws org.jivesoftware.openfire.user.UserAlreadyExistsException 
     * @throws org.jivesoftware.openfire.SharedGroupException 
     */
    public void syncRoster(String username, List<TransferRosterItem> rosterList) 
            throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException
    {
        
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        
        Set<String> jidInRosterList = new HashSet<String>();
        for(TransferRosterItem tri : rosterList){
            JID j = new JID(tri.jid);
            jidInRosterList.add(j.toBareJID().toString());
        }
        
        for(TransferRosterItem tri : rosterList){
            JID j = new JID(tri.jid);
            RosterItem ri = r.getRosterItem(j);
            
            List<String> groups = new ArrayList<String>();
            if (tri.groups != null) {
                StringTokenizer tkn = new StringTokenizer(tri.groups, ",");
                while (tkn.hasMoreTokens()) {
                    groups.add(tkn.nextToken());
                }
            }
            
            // If null, roster item does not exist, thus create it.
            if (ri==null){
                 ri = r.createRosterItem(j, tri.name, groups, false, true);
            } else {
                // Already exists, updating.
                ri.setGroups(groups);
                ri.setNickname(tri.name);
            }
            
            // In both cases.
            ri.setSubStatus(RosterItem.SubType.getTypeFromInt(tri.subscription));
            if (tri.askStatus!=null){
                ri.setAskStatus(RosterItem.AskType.getTypeFromInt(tri.askStatus));
            }
            if (tri.recvStatus!=null){
                ri.setRecvStatus(RosterItem.RecvType.getTypeFromInt(tri.recvStatus));
            }
            
            r.updateRosterItem(ri);
        }
        
        // Delete non-existent roster items from the roster.
        Collection<RosterItem> rosterItems = r.getRosterItems();
        for(RosterItem ri : rosterItems){
            JID j = ri.getJid();
            String jid = j.asBareJID().toString();
            if (jidInRosterList.contains(jid)==false){
                // Delete from the roster if not present in the sync list.
                try {
                    r.deleteRosterItem(j, true);
                } catch(Exception ex){
                    ;
                }
            }
        }
    }
    
    /**
     * Fetches roster for the user.
     * @param username 
     * @return  
     * @throws org.jivesoftware.openfire.user.UserNotFoundException  
     * @throws org.jivesoftware.openfire.user.UserAlreadyExistsException  
     * @throws org.jivesoftware.openfire.SharedGroupException  
     */
    public List<TransferRosterItem> fetchRoster(String username) 
        throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        List<TransferRosterItem> tRosterItems = new LinkedList<TransferRosterItem>();
        
        // Delete non-existent roster items from the roster.
        Collection<RosterItem> rosterItems = r.getRosterItems();
        for(RosterItem ri : rosterItems){
            JID j = ri.getJid();
            String jid = j.asBareJID().toString();
            
            TransferRosterItem tri = new TransferRosterItem();
            tri.jid = jid;
            tri.name = ri.getNickname();
            tri.askStatus = ri.getAskStatus().getValue();
            tri.recvStatus = ri.getRecvStatus().getValue();
            tri.subscription = ri.getSubStatus().getValue();
            
            List<String> groups = ri.getGroups();
            if (groups==null || groups.isEmpty()){
                tri.groups = "";
            } else{
                tri.groups = StringUtils.collectionToString(groups);
            }
            
            tRosterItems.add(tri);
        }
        
        return tRosterItems;
    }
    
    /**
     * Fetches all users in the system.
     * @return 
     */
    public List<String> fetchUsers(){
        return new LinkedList<String>(userManager.getUsernames());
    }

    /**
     * Add new roster item for specified user
     *
     * @param username the username of the local user to add roster item to.
     * @param itemJID the JID of the roster item to be added.
     * @param itemName the nickname of the roster item.
     * @param subscription the type of subscription of the roster item. Possible values are: -1(remove), 0(none), 1(to), 2(from), 3(both).
     * @param groupNames the name of a group to place contact into.
     * @throws UserNotFoundException if the user does not exist in the local server.
     * @throws UserAlreadyExistsException if roster item with the same JID already exists.
     * @throws SharedGroupException if roster item cannot be added to a shared group.
     */
    public void addRosterItem(String username, String itemJID, String itemName, String subscription, String groupNames)
            throws UserNotFoundException, UserAlreadyExistsException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        try {
            r.getRosterItem(j);
            throw new UserAlreadyExistsException(j.toBareJID());
        }
        catch (UserNotFoundException e) {
            //Roster item does not exist. Try to add it.
        }

        if (r != null) {
            List<String> groups = new ArrayList<String>();
            if (groupNames != null) {
                StringTokenizer tkn = new StringTokenizer(groupNames, ",");
                while (tkn.hasMoreTokens()) {
                    groups.add(tkn.nextToken());
                }
            }
            RosterItem ri = r.createRosterItem(j, itemName, groups, false, true);
            if (subscription == null) {
                subscription = "0";
            }
            ri.setSubStatus(RosterItem.SubType.getTypeFromInt(Integer.parseInt(subscription)));
            r.updateRosterItem(ri);
        }
    }

    /**
     * Update roster item for specified user
     *
     * @param username the username of the local user to update roster item for.
     * @param itemJID the JID of the roster item to be updated.
     * @param itemName the nickname of the roster item.
     * @param subscription the type of subscription of the roster item. Possible values are: -1(remove), 0(none), 1(to), 2(from), 3(both).
     * @param groupNames the name of a group.
     * @throws UserNotFoundException if the user does not exist in the local server or roster item does not exist.
     * @throws SharedGroupException if roster item cannot be added to a shared group.
     */
    public void updateRosterItem(String username, String itemJID, String itemName, String subscription, String groupNames)
            throws UserNotFoundException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        RosterItem ri = r.getRosterItem(j);

        List<String> groups = new ArrayList<String>();
        if (groupNames != null) {
            StringTokenizer tkn = new StringTokenizer(groupNames, ",");
            while (tkn.hasMoreTokens()) {
                groups.add(tkn.nextToken());
            }
        }

        ri.setGroups(groups);
        ri.setNickname(itemName);

        if (subscription == null) {
            subscription = "0";
        }
        ri.setSubStatus(RosterItem.SubType.getTypeFromInt(Integer.parseInt(subscription)));
        r.updateRosterItem(ri);
    }

    /**
     * Delete roster item for specified user. No error returns if nothing to delete.
     *
     * @param username the username of the local user to add roster item to.
     * @param itemJID the JID of the roster item to be deleted.
     * @throws UserNotFoundException if the user does not exist in the local server.
     * @throws SharedGroupException if roster item cannot be deleted from a shared group.
     */
    public void deleteRosterItem(String username, String itemJID)
            throws UserNotFoundException, SharedGroupException
    {
        getUser(username);
        Roster r = rosterManager.getRoster(username);
        JID j = new JID(itemJID);

        // No roster item is found. Uncomment the following line to throw UserNotFoundException.
        //r.getRosterItem(j);

        r.deleteRosterItem(j, true);
    }

    /**
     * Returns the the requested user or <tt>null</tt> if there are any
     * problems that don't throw an error.
     *
     * @param username the username of the local user to retrieve.
     * @return the requested user.
     * @throws UserNotFoundException if the requested user
     *         does not exist in the local server.
     */
    private User getUser(String username) throws UserNotFoundException {
        JID targetJID = server.createJID(username, null);
        // Check that the sender is not requesting information of a remote server entity
        if (targetJID.getNode() == null) {
            // Sender is requesting presence information of an anonymous user
            throw new UserNotFoundException("Username is null");
        }
        return userManager.getUser(targetJID.getNode());
    }

    /**
     * Returns the secret key that only valid requests should know.
     *
     * @return the secret key.
     */
    public String getSecret() {
        return secret;
    }

    /**
     * Sets the secret key that grants permission to use the userservice.
     *
     * @param secret the secret key.
     */
    public void setSecret(String secret) {
        JiveGlobals.setProperty("plugin.userservice.secret", secret);
        this.secret = secret;
    }

    public Collection<String> getAllowedIPs() {
        return allowedIPs;
    }

    public void setAllowedIPs(Collection<String> allowedIPs) {
        JiveGlobals.setProperty("plugin.userservice.allowedIPs", StringUtils.collectionToString(allowedIPs));
        this.allowedIPs = allowedIPs;
    }

    /**
     * Returns true if the user service is enabled. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @return true if the user service is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables or disables the user service. If not enabled, it will not accept
     * requests to create new accounts.
     *
     * @param enabled true if the user service should be enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        JiveGlobals.setProperty("plugin.userservice.enabled",  enabled ? "true" : "false");
    }

    public void propertySet(String property, Map<String, Object> params) {
        if (property.equals("plugin.userservice.secret")) {
            this.secret = (String)params.get("value");
        }
        else if (property.equals("plugin.userservice.enabled")) {
            this.enabled = Boolean.parseBoolean((String)params.get("value"));
        }
        else if (property.equals("plugin.userservice.allowedIPs")) {
            this.allowedIPs = StringUtils.stringToCollection((String)params.get("value"));
        }
    }

    public void propertyDeleted(String property, Map<String, Object> params) {
        if (property.equals("plugin.userservice.secret")) {
            this.secret = "";
        }
        else if (property.equals("plugin.userservice.enabled")) {
            this.enabled = false;
        }
        else if (property.equals("plugin.userservice.allowedIPs")) {
            this.allowedIPs = Collections.emptyList();
        }
    }

    public void xmlPropertySet(String property, Map<String, Object> params) {
        // Do nothing
    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {
        // Do nothing
    }
}
