/**
 * $RCSfile$
 * $Revision: 1710 $
 * $Date: 2005-07-26 11:56:14 -0700 (Tue, 26 Jul 2005) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.plugin.userService;

import gnu.inet.encoding.Stringprep;
import org.bouncycastle.util.encoders.Base64;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.jivesoftware.admin.AuthCheckFilter;
import org.jivesoftware.openfire.SharedGroupException;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.roster.TransferRosterItem;
import org.jivesoftware.openfire.user.UserAlreadyExistsException;
import org.jivesoftware.openfire.user.UserNotFoundException;
import org.jivesoftware.util.Log;
import org.xmpp.packet.JID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;


/**
 * Servlet that addition/deletion/modification of the users info in the system.
 * Use the <b>type</b>
 * parameter to specify the type of action. Possible values are <b>add</b>,<b>delete</b> and
 * <b>update</b>. <p>
 * <p/>
 * The request <b>MUST</b> include the <b>secret</b> parameter. This parameter will be used
 * to authenticate the request. If this parameter is missing from the request then
 * an error will be logged and no action will occur.
 *
 * @author Justin Hunt
 */
public class UserServiceServlet extends HttpServlet {

    private UserServicePlugin plugin;


    @Override
	public void init(ServletConfig servletConfig) throws ServletException {
        super.init(servletConfig);
        plugin = (UserServicePlugin) XMPPServer.getInstance().getPluginManager().getPlugin("userservice");
 
        // Exclude this servlet from requiring the user to login
        AuthCheckFilter.addExclude("userService/userservice");
    }

    @Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
    {
        // Printwriter for writing out responses to browser
        PrintWriter out = response.getWriter();

        if (!plugin.getAllowedIPs().isEmpty()) {
            // Get client's IP address from the request.
            String ipAddress = request.getRemoteAddr();
            
            //
            // Do not use headers since it is very easy to spoof them.
            // Unles we were using local proxy server installed on the 
            // machine controlled by us.
            //
//            String ipAddress = request.getHeader("x-forwarded-for");
//            if (ipAddress == null) {
//                ipAddress = request.getHeader("X_FORWARDED_FOR");
//                if (ipAddress == null) {
//                    ipAddress = request.getHeader("X-Forward-For");
//                    if (ipAddress == null) {
//                        ipAddress = request.getRemoteAddr();
//                    }
//                }
//            }
            
            if (!plugin.getAllowedIPs().contains(ipAddress)) {
                Log.warn("User service rejected service to IP address: " + ipAddress);
                replyError("RequestNotAuthorised",response, out);
                return;
            }
        }
        
        // Check that our plugin is enabled.
        if (!plugin.isEnabled()) {
            Log.warn("User service plugin is disabled: " + request.getQueryString());
            replyError("UserServiceDisabled",response, out);
            return;
        }

        request.setCharacterEncoding("utf-8");
        final String type = request.getParameter("type");
        final String secret = request.getParameter("secret");
        
        // In case of roster-sync, the whole user roster is passed in the request
        // using BASE64 encoding of the JSON-encoded list of the contacts.
        String roster = request.getParameter("roster");
        if (roster!=null){
            roster = java.net.URLDecoder.decode(roster, "UTF-8");
        }
        
        //No defaults, add, delete, update only
        //type = type == null ? "image" : type;
       
        // Check this request is authorised
        if (secret == null || !secret.equals(plugin.getSecret())){
            Log.warn("An unauthorised user service request was received: " + request.getQueryString());
            replyError("RequestNotAuthorised",response, out);
            return;
         }
        
        if(type.equals("sync_roster") && (roster==null || roster.isEmpty())){
            replyError("IllegalArgumentException",response, out);
            return;
        }

        // Check the request type and process accordingly
        try {
            // Bulk roster sync has almost no parameters.
            if ("sync_roster_bulk".equals(type)){
                try {
                    final String jsonReq = request.getParameter("jsonReq");
                    plugin.bulkSyncRosterInExecutor(jsonReq);
                    replyMessage("ok", response, out);

                } catch(Exception e){
                    Log.warn("Exception for roster sync JSON=", e);
                    replyError("IllegalArgumentException",response, out);
                }
                return;
            }

            // Some checking is required on the username
            String username = java.net.URLDecoder.decode(request.getParameter("username"), "UTF-8");
            if (username == null){
                replyError("IllegalArgumentException",response, out);
                return;
            }

            username = username.trim().toLowerCase();
            username = JID.escapeNode(username);
            username = Stringprep.nodeprep(username);
            Log.info(String.format("REQ: %s, usrname=%s", type, username));

            String password = request.getParameter("password");
            String name = request.getParameter("name");
            String email = request.getParameter("email");
            String groupNames = request.getParameter("groups");
            String item_jid = request.getParameter("item_jid");
            String sub = request.getParameter("subscription");

            if ((type.equals("add_roster") || type.equals("update_roster") || type.equals("delete_roster")) &&
                    (item_jid == null || !(sub == null || sub.equals("-1") || sub.equals("0") ||
                            sub.equals("1") || sub.equals("2") || sub.equals("3")))) {
                replyError("IllegalArgumentException",response, out);
                return;
            }

            if ("add".equals(type)) {
                plugin.createUser(username, password, name, email, groupNames);
                replyMessage("ok",response, out);
                //imageProvider.sendInfo(request, response, presence);
            }
            else if ("delete".equals(type)) {
                plugin.deleteUser(username);
                replyMessage("ok",response,out);
                //xmlProvider.sendInfo(request, response, presence);
            }
            else if ("enable".equals(type)) {
                plugin.enableUser(username);
                replyMessage("ok",response,out);
            }
            else if ("disable".equals(type)) {
                plugin.disableUser(username);
                replyMessage("ok",response,out);
            }
            else if ("update".equals(type)) {
                plugin.updateUser(username, password,name,email, groupNames);
                replyMessage("ok",response,out);
                //xmlProvider.sendInfo(request, response, presence);
            }
            else if ("add_roster".equals(type)) {
                plugin.addRosterItem(username, item_jid, name, sub, groupNames);
                replyMessage("ok",response, out);
            }
            else if ("update_roster".equals(type)) {
                plugin.updateRosterItem(username, item_jid, name, sub, groupNames);
                replyMessage("ok",response, out);
            }
            else if ("delete_roster".equals(type)) {
                plugin.deleteRosterItem(username, item_jid);
                replyMessage("ok",response, out);
            }
            else if ("sync_roster".equals(type)){
                List<TransferRosterItem> rosterList = null;
                String rosterJson = null;
                try {
                    rosterJson = new String(Base64.decode(roster), "UTF-8");
                    ObjectMapper mapper = new ObjectMapper();
                    rosterList = mapper.readValue(rosterJson, new TypeReference<List<TransferRosterItem>>(){});

                    plugin.syncRosterInExecutor(username, rosterList);
                    replyMessage("ok",response, out);
                } catch(Exception e){
                    Log.warn("Exception for user ["+username+"] JSON=", e);
                    replyError("IllegalArgumentException",response, out);
                }
            }
            else if ("fetch_roster".equals(type)){
                List<TransferRosterItem> fetchRoster = plugin.fetchRoster(username);
                
                ObjectMapper mapper = new ObjectMapper();
                String rosterJSON = mapper.writeValueAsString(fetchRoster);
                replyMessage(rosterJSON,response, out);
            }
            else if ("fetch_users".equals(type)){
                List<String> fetchUsers = plugin.fetchUsers();
                
                ObjectMapper mapper = new ObjectMapper();
                String userlistJSON = mapper.writeValueAsString(fetchUsers);
                replyMessage(userlistJSON,response, out);
            }
            else if ("echo".equals(type)){
                replyMessage(username,response, out);
            }
            else {
                Log.warn("The userService servlet received an invalid request of type: " + type);
                replyError("IllegalRequestException",response, out);
            }
        }
        catch (UserAlreadyExistsException e) {
            replyError("UserAlreadyExistsException",response, out);
        }
        catch (UserNotFoundException e) {
            replyError("UserNotFoundException",response, out);
        }
        catch (IllegalArgumentException e) {
            replyError("IllegalArgumentException",response, out);
        }
        catch (SharedGroupException e) {
            replyError("SharedGroupException",response, out);
        }
        catch (Exception e) {
            replyError(e.toString(),response, out);
        } 
    }

    private void replyMessage(String message,HttpServletResponse response, PrintWriter out){
        response.setContentType("text/xml");        
        out.println("<result>" + message + "</result>");
        out.flush();
    }

    private void replyError(String error,HttpServletResponse response, PrintWriter out){
        response.setContentType("text/xml");        
        out.println("<error>" + error + "</error>");
        out.flush();
    }
    
    @Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        doGet(request, response);
    }

    @Override
	public void destroy() {
        super.destroy();
        // Release the excluded URL
        AuthCheckFilter.removeExclude("userService/userservice");
    }
}
