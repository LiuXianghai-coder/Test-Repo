package org.xhliu.thread.base;

public class Parent {
    public synchronized void parentDo() {
        // do something.......
    }

    static long value = 0L;

    static final Object object = new Object();

    static class Op implements Runnable {
        public void run() {
            for (int i = 0; i < 10; ++i) {
                value++;
            }
        }
    }

    public static void main(String[] args) {
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; ++i)
            threads[i] = new Thread(new Op());

        for (Thread thread : threads) thread.start();

        System.out.println("final value=" + value);
    }
}
