package org.xhliu.thread.aqs;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class TwoInLock implements Lock {
    private final Sync sync = new Sync(2);

    static class Sync extends AbstractQueuedSynchronizer {

        public Sync(int count) {
            setState(count);
        }

        @Override
        protected int tryAcquireShared(int arg) {
            for (;;) {
                int cnt = getState();
                if (cnt == 0) {
                    return -1;
                }

                int newState = cnt - arg;
                if (newState < 0 || compareAndSetState(cnt, cnt - 1))
                    return newState;
            }
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            for (;;) {
                int cur = getState();
                int newCnt = cur + arg;
                if (compareAndSetState(cur, newCnt))
                    return true;
            }
        }
    }

    @Override
    public void lock() {
        sync.acquireShared(1);
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return false;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void unlock() {
        sync.releaseShared(1);
    }

    @Override
    public Condition newCondition() {
        return null;
    }

    public static void main(String[] args) {
        final TwoInLock lock = new TwoInLock();
        class MyThread implements Runnable {
            @Override
            public void run() {
                lock.lock();
                try {
                    Thread.sleep(100);
                    System.out.println(Thread.currentThread().getName());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        }

        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; ++i)
            threads[i] = new Thread(new MyThread(), "Thread-" + i);

        for (Thread thread : threads) thread.start();
    }
}
