package org.xhliu.thread.aqs;

import java.util.concurrent.CountDownLatch;

public class CountDownExample {

    public static void main(String[] args) {
        CountDownLatch latch = new CountDownLatch(2);
        Thread t1 = new Thread(() -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            latch.countDown();
        }, "t1");

        Thread t2 = new Thread(() -> {
           try {
               Thread.sleep(5000);
           } catch (InterruptedException e) {
               e.printStackTrace();
           }
           latch.countDown();
        }, "t2");

        Thread t3 = new Thread(() -> {
            try {
                latch.await();
                System.out.println("Thread-3 开始执行。。。");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "t3");

        Thread t4 = new Thread(() -> {
            try {
                latch.await();
                System.out.println("Thread-4 开始执行。。。");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "t4");

        t1.start();
        t2.start();

        t3.start();
        t4.start();
    }
}
