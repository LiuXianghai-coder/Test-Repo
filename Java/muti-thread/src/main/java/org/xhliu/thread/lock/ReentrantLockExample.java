package org.xhliu.thread.lock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockExample {
    private final static Lock lock = new ReentrantLock();
    static int value = 0;

    public static void main(String[] args) {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < 10; ++i) {
            threads[i] = new Thread(new Demo());
            threads[i].start();
        }
    }

    static class Demo implements Runnable {
        @Override
        public void run() {
            lock.lock();
            try {
                value++;
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                lock.unlock();
            }
        }
    }
}
