package org.nuxeo.ecm.platform.audit.service;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.audit.service.extension.BulkConfigDescriptor;
import org.nuxeo.runtime.metrics.MetricsService;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

public class AuditBulker {

    final Log log = LogFactory.getLog(AuditBulker.class);

    final AbstractAuditBackend backend;

    protected final BulkConfigDescriptor config;

    protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected final Counter queuedCount = registry.counter(MetricRegistry.name("nuxeo", "audit", "queued"));

    protected final Counter drainedCount = registry.counter(MetricRegistry.name("nuxeo", "audit", "drained"));

    Thread thread;

    AuditBulker(AbstractAuditBackend backend, BulkConfigDescriptor config) {
        this.backend = backend;
        this.config = config;
    }

    void startup() {
        thread = new Thread(new Consumer(), "Nuxeo-Audit-Bulker");
        thread.start();
    }

    void shutdown() {
        stopped = true;
        try {
            thread.interrupt();
        } finally {
            thread = null;
        }
    }

    final ReentrantLock lock = new ReentrantLock();

    final Condition isEmpty = lock.newCondition();

    final Condition isFilled = lock.newCondition();

    final Queue<LogEntry> queue = new ConcurrentLinkedQueue<>();

    volatile boolean stopped;

    void offer(LogEntry entry) {
        if (log.isDebugEnabled()) {
            log.debug("offered " + entry);
        }
        queue.add(entry);
        queuedCount.inc();
        if (queue.size() >= config.size) {
            lock.lock();
            try {
                isFilled.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    boolean await(long time, TimeUnit unit) throws InterruptedException {
        if (queue.isEmpty()) {
            return true;
        }
        lock.lock();
        try {
            isFilled.signalAll();
            return isEmpty.await(time, unit);
        } finally {
            lock.unlock();
        }
    }

    int drain() {
        List<LogEntry> entries = new LinkedList<>();
        while (!queue.isEmpty()) {
            entries.add(queue.remove());
        }
        backend.addLogEntries(entries);
        drainedCount.inc(entries.size());
        if (queue.isEmpty()) {
            lock.lock();
            try {
                isEmpty.signalAll();
            } finally {
                lock.unlock();
            }
        }
        return entries.size();
    }


    class Consumer implements Runnable {

        @Override
        public void run() {
            log.info("bulk audit logger started");
            while(!stopped) {
                lock.lock();
                try {
                    isFilled.await(config.timeout, TimeUnit.SECONDS);
                    if (queue.isEmpty()) {
                        continue;
                    }
                } catch (InterruptedException cause) {
                    Thread.currentThread().interrupt();
                    return;
                } finally {
                    lock.unlock();
                }
                try {
                    int count = drain();
                    if (log.isDebugEnabled()) {
                        log.debug("flushed " + count + " events");
                    }
                } catch (RuntimeException cause) {
                    log.error("caught error while draining audit queue", cause);
                }
            }
            log.info("bulk audit logger stopped");
        }

    }
}
