package org.jivesoftware.openfire.plugin.userService.utils;

import java.util.ArrayList;

/**
 * Job logger to trace job progress, for debugging purposes, trace log level to reveal cause of deadlocks for long running jobs.
 * Created by dusanklinec on 04.09.15.
 */
public class JobLoggerImpl implements JobLogger {

    private static final int DEFAULT_MSG_LIMIT = 50;
    private final ArrayList<String> messages = new ArrayList<String>(3);
    private int msgLimit = 3;
    private int lastOffset = -1;
    private int numMessages = 0;

    public JobLoggerImpl() {
        setMessagesNumber(DEFAULT_MSG_LIMIT);
    }

    @Override
    public synchronized void setMessagesNumber(int msgNum) {
        messages.ensureCapacity(msgNum);
        messages.clear();
        msgLimit = msgNum;
        for(int i=0; i<msgNum; i++){
            messages.add("");
        }
    }

    @Override
    public void logMsg(String msg, Object... args) {
        logMsg(6, "", msg, args);
    }

    @Override
    public synchronized void logMsg(int logLevel, String tag, String msg, Object... args) {
        messages.ensureCapacity(msgLimit);
        lastOffset = (lastOffset + 1) % msgLimit;

        final long threadId = Thread.currentThread().getId();
        final String logmsg = String.format("[%s] #%s %s %s: %s", logLevel, threadId, System.currentTimeMillis(), tag, String.format(msg, args));

        messages.set(lastOffset, logmsg);
        numMessages = numMessages >= msgLimit ? msgLimit : numMessages + 1;
    }

    @Override
    public synchronized String dumpMessages() {
        final StringBuilder sb = new StringBuilder();
        for(int i=0; i<numMessages && lastOffset >= 0; i++){
            final int curPos = (lastOffset+i) % msgLimit;
            sb.append(messages.get(curPos));
            sb.append("\n");
        }

        return sb.toString();
    }
}
