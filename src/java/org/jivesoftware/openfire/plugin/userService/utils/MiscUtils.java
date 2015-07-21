package org.jivesoftware.openfire.plugin.userService.utils;

import java.util.Collection;

/**
 * Created by dusanklinec on 15.07.15.
 */
public class MiscUtils {
    public static int collectionSize(Collection c){
        return c==null ? 0 : c.size();
    }
}
