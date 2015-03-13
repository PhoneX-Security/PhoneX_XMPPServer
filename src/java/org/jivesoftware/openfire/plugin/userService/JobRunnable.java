package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;

/**
 * Created by dusanklinec on 13.03.15.
 */
public interface JobRunnable {
    void run(UserServicePlugin plugin);
}
