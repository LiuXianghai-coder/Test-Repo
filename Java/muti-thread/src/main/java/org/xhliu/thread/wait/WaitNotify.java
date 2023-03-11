package org.xhliu.thread.wait;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WaitNotify {
    static AtomicBoolean flag = new AtomicBoolean(true);
    final static Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        Thread waitThread = new Thread(new Wait(), "Wait-Thread");
        waitThread.start();
        TimeUnit.SECONDS.sleep(1);
        Thread notifyThread = new Thread(new Notify(), "Notify-Thread");
        notifyThread.start();
    }

    static class Wait implements Runnable {
        @Override
        public void run() {
            synchronized (lock) {
                while (flag.get()) {
                    try {
                        System.out.println(Thread.currentThread() + " flag is true. wait @ "
                                + formatDate());
                        lock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                System.out.println(Thread.currentThread() + " flag is false. running @ " + formatDate());
            }
        }
    }

    static class Notify implements Runnable {
        @Override
        public void run() {
            synchronized (lock) {
                System.out.println(Thread.currentThread() + " hold lock. notify @ "
                        + formatDate());
                lock.notifyAll();
                flag.set(false);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            try {
                TimeUnit.MILLISECONDS.sleep(0);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            synchronized (lock) {
                System.out.println(Thread.currentThread() + " hold lock again. sleep @ "
                        + formatDate());
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static String formatDate() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }
}
