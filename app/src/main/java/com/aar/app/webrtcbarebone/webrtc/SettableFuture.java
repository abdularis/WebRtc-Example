package com.aar.app.webrtcbarebone.webrtc;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SettableFuture<T> implements Future<T> {

    private boolean mCancelled;
    private boolean mCompleted;
    private Exception mError;
    private T mResult;

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (!mCompleted && !mCancelled) {
            mCancelled = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean isCancelled() {
        return mCancelled;
    }

    @Override
    public boolean isDone() {
        return mCompleted;
    }

    @Override
    public synchronized T get() throws ExecutionException, InterruptedException {
        while (!mCompleted) wait();

        if (mError != null) throw new ExecutionException(mError);
        return mResult;
    }

    @Override
    public synchronized T get(long timeout, TimeUnit unit)
            throws ExecutionException, InterruptedException, TimeoutException {
        // ToDo implement get with timeout
        return get();
    }

    public synchronized void set(T result) {
        if (mCompleted || mCancelled) return;

        mResult = result;
        mCompleted = true;
        notifyAll();
    }

    public synchronized void setError(Exception error) {
        if (mCompleted || mCancelled) return;

        mError = error;
        mCompleted = true;
        notifyAll();
    }
}
