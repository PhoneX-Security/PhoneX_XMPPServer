package org.jivesoftware.openfire.plugin.userService.utils;

/**
 * Job logger that does nothing. Default for most jobs.
 *
 * Created by dusanklinec on 04.09.15.
 */
public class NullJobLogger implements JobLogger {
    public static final JobLogger INSTANCE = new NullJobLogger();

    @Override
    public void setMessagesNumber(int msgNum) {

    }

    @Override
    public void logMsg(int logLevel, String tag, String msg, Object... args) {

    }

    @Override
    public void logMsg(String msg, Object... args) {

    }

    @Override
    public String dumpMessages() {
        return null;
    }
}
