package org.xhliu.thread.lock;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ReentrantLockExample {
    private final static Lock lock = new ReentrantLock();
    static int value = 0;

    public static void main(String[] args) {
    }

    static class Demo implements Runnable {
        @Override
        public void run() {
            lock.lock();
            try {
                value++;
            } finally {
                lock.unlock();
            }
        }
    }
}
