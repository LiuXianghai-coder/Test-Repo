package org.xhliu.thread.aqs;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

public class BarrierTest {
    static final CyclicBarrier barrier = new CyclicBarrier(10);

    static class Worker implements Runnable {
        @Override
        public void run() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println(Thread.currentThread().getName() + " finished.");

            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        Thread[] threads = new Thread[10];
        for (int i = 0; i< 10; ++i)
            threads[i] = new Thread(new Worker(), "Thread-" + i);

        for (Thread thread : threads) thread.start();
    }
}
