package org.jivesoftware.openfire.plugin.userService.utils;

/**
 * Created by dusanklinec on 04.09.15.
 */
public interface JobLogger {


    /**
     * Number of messages to keep.
     * @param msgNum
     */
    void setMessagesNumber(int msgNum);

    /**
     * Log messages.
     * @param logLevel
     * @param tag
     * @param msg
     * @param args
     */
    void logMsg(int logLevel, String tag, final String msg, final Object... args);
    void logMsg(final String msg, final Object... args);

    /**
     * Dump stored log messages.
     * @return
     */
    String dumpMessages();
}
