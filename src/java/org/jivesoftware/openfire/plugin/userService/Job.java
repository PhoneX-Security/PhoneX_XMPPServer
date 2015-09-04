package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.jivesoftware.openfire.plugin.userService.utils.JobLogger;
import org.jivesoftware.openfire.plugin.userService.utils.NullJobLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for jobs execution.
 */
public class Job implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Job.class);
    private static final AtomicInteger ctr = new AtomicInteger(0);

    private final JobRunnable job;
    private String name;
    private WeakReference<UserServicePlugin> svc;
    private final String id;
    private JobLogger logger = NullJobLogger.INSTANCE;

    public Job(JobRunnable job) {
        this.job = job;
        this.id = String.valueOf(ctr.getAndIncrement());
    }

    public Job(String name, JobRunnable job) {
        this.name = name;
        this.job = job;
        this.id = String.valueOf(ctr.getAndIncrement());
    }

    @Override
    public void run() {
        try {
            job.run(svc == null ? null : svc.get(), this);
        } catch(Throwable e){
            log.error(String.format("Exception in executing a job %s", name), e);
        }
    }

    public void setSvc(UserServicePlugin svc) {
        this.svc = new WeakReference<UserServicePlugin>(svc);
    }

    public JobRunnable getJob() {
        return job;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public JobLogger getLogger() {
        return logger;
    }

    public void setLogger(JobLogger logger) {
        this.logger = logger;
    }
}
