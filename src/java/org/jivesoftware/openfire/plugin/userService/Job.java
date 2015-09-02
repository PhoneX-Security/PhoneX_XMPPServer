package org.jivesoftware.openfire.plugin.userService;

import org.jivesoftware.openfire.plugin.UserServicePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;

/**
 * Base class for jobs execution.
 */
public class Job implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Job.class);

    private final JobRunnable job;
    private String name;
    private WeakReference<UserServicePlugin> svc;

    public Job(JobRunnable job) {
        this.job = job;
    }

    public Job(String name, JobRunnable job) {
        this.name = name;
        this.job = job;
    }

    @Override
    public void run() {
        try {
            job.run(svc == null ? null : svc.get());
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
}
