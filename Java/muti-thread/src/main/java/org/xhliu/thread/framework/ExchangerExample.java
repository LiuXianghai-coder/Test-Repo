package org.xhliu.thread.framework;

import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExchangerExample {
    private static final Exchanger<String> exgr = new Exchanger<>();
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(2);

    public static void main(String[] args) {
        Thread threadA = new Thread(() -> {
            String A = "银行流水A";    // A录入银行流水数据
            try {
                Thread.sleep(2000);
                String B = exgr.exchange(A);
                System.out.println("1: A和B数据是否一致:" + A.equals(B) + "，A录入的是:" + A + "，B录入是:" + B +
                        "\tCurrent Thread: " + Thread.currentThread().getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try {
                Thread.sleep(2000);
                String B = "银行流水B";    // B录入银行流水数据
                Thread.sleep(2000);
                String A = exgr.exchange(B);
                System.out.println("1: A和B数据是否一致:" + A.equals(B) + "，A录入的是:" + A + "，B录入是:" + B +
                        "\tCurrent Thread: " + Thread.currentThread().getName());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "Thread-B");

        threadPool.execute(threadA);
        threadPool.execute(threadB);

        threadPool.shutdown();

//        threadA.start();
//        threadB.start();
    }
}
