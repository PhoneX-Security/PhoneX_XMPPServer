package org.jivesoftware.openfire.plugin.userService.platformPush;

import org.jivesoftware.openfire.plugin.userService.utils.LRUCache;
import org.xmpp.packet.JID;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Cache for the device tokens stored for individual user.
 * Idea: queries for user/resource + queries of the type: Has a given user any working device token?
 * Created by dusanklinec on 13.07.15.
 */
public class TokenCache {
    private static final int CACHE_SIZE = 128;

    /**
     * Cache user@domain.net/resource -> tokenConfig.
     */
    private final LRUCache<JID, TokenConfig> tokenCache = new LRUCache<JID, TokenConfig>(CACHE_SIZE);

    /**
     * Cache user@domain.net -> Set<TokenConfig>
     */
    private final LRUCache<String, Set<TokenConfig>> tokenUsrCache = new LRUCache<String, Set<TokenConfig>>(CACHE_SIZE);

    /**
     * Flushes all caches.
     */
    public void clear(){
        tokenCache.clear();
        tokenUsrCache.clear();
    }

    /**
     * Removes user from the token cache.
     * @param user
     */
    public synchronized void remove(JID user){
        final String res = user.getResource();
        final boolean resEmpty = res == null || res.isEmpty();
        if (!resEmpty){
            tokenCache.remove(user);
        }

        final Set<TokenConfig> tokens = tokenUsrCache.get(user.toBareJID());
        if (resEmpty){
            tokens.clear();
            return;
        }

        final Iterator<TokenConfig> it = tokens.iterator();
        while(it.hasNext()){
            final TokenConfig token = it.next();
            final JID tokenUser = token.getUser();
            if (user.equals(tokenUser)){
                it.remove();
            }
        }
    }

    /**
     * Updates token for the given user in the cache.
     * @param user
     * @param token
     */
    public synchronized void updateToken(JID user, TokenConfig token){
        // TODO: implement.
    }

    public TokenConfig getToken(JID user){
        // TODO: implement.
        return null;
    }

    public Set<TokenConfig> getTokens(String user){
        // TODO: implement.
        return null;
    }

}
