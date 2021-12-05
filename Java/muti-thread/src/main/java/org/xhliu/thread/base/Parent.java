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
        Thread thread = new Thread(() -> {});
        thread.start();
    }
}
