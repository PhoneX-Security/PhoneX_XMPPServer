package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.utils.JobLogger;

/**
 * Created by dusanklinec on 13.03.15.
 */
public interface JobRunnable {
    void run(UserServicePlugin plugin, Job job);
}
