package org.xhliu.thread.wait;

import java.util.concurrent.TimeUnit;

public class JoinExample {
    public static void main(String[] args) throws InterruptedException {
        Thread previous = Thread.currentThread();

        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(new DemoInfo(previous), String.valueOf(i));
            thread.start();
            previous = thread;
        }

        TimeUnit.SECONDS.sleep(3);
        System.out.println(Thread.currentThread().getName() + " terminated.");
    }

    static class DemoInfo implements Runnable {
        private final Thread thread;

        DemoInfo(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void run() {
            try {
              thread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName() + " terminated.");
        }
    }
}
