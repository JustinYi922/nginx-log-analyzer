package com.gwamcc.nginxlog.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ImportProgress {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong totalLines = new AtomicLong(0);
    private final AtomicLong parsed = new AtomicLong(0);
    private final AtomicLong inserted = new AtomicLong(0);
    private final AtomicLong skipped = new AtomicLong(0);
    private volatile String filePath;
    private volatile String message = "idle";
    private volatile String error;
    private volatile long startedAt;
    private volatile long finishedAt;

    public boolean tryStart(String filePath) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        this.filePath = filePath;
        this.message = "running";
        this.error = null;
        this.startedAt = System.currentTimeMillis();
        this.finishedAt = 0L;
        totalLines.set(0);
        parsed.set(0);
        inserted.set(0);
        skipped.set(0);
        return true;
    }

    public void finishOk(String message) {
        this.message = message;
        this.finishedAt = System.currentTimeMillis();
        running.set(false);
    }

    public void finishError(String error) {
        this.error = error;
        this.message = "failed";
        this.finishedAt = System.currentTimeMillis();
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getTotalLines() {
        return totalLines.get();
    }

    public void setTotalLines(long n) {
        totalLines.set(n);
    }

    public long getParsed() {
        return parsed.get();
    }

    public void addParsed(long n) {
        parsed.addAndGet(n);
    }

    public long getInserted() {
        return inserted.get();
    }

    public void addInserted(long n) {
        inserted.addAndGet(n);
    }

    public long getSkipped() {
        return skipped.get();
    }

    public void addSkipped(long n) {
        skipped.addAndGet(n);
    }

    public String getFilePath() {
        return filePath;
    }

    public String getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public double getPercent() {
        long total = totalLines.get();
        if (total <= 0) {
            return 0D;
        }
        return Math.min(100D, (parsed.get() + skipped.get()) * 100D / total);
    }
}
