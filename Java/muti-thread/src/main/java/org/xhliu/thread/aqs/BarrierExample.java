package org.xhliu.thread.aqs;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ThreadLocalRandom;

public class BarrierExample {
    private final CyclicBarrier barrier;

    private final int[] array;

    private final int[] aux;

    private final Thread[] threads;

    private final CountDownLatch startGate;
    private final CountDownLatch endGate;

    class MergeAction implements Runnable {
        private final int[] idxs;
        private final int unit;

        MergeAction(int[] idxs, int unit) {
            this.idxs = idxs;
            this.unit = unit;

            for (int i = 0; i < idxs.length; ++i) {
                idxs[i] = i * unit;
            }
        }

        @Override
        public void run() {
            int idx = 0;
            int n = idxs.length;
//            System.out.println("Before===================");
//            System.out.println(Arrays.toString(array));
            while (idx < array.length) {
                int index = 0;
                int tmp = 0, min = Integer.MAX_VALUE;
                for (int i = 0; i < n; ++i) {
//                    System.out.println("idx[i]=" + idxs[i] + "\tlen=" + array.length + "\tunit=" + (i + 1) * unit);
                    if (idxs[i] >= array.length || idxs[i] >= (i + 1) * unit)
                        continue;
                    int j = idxs[i];
                    if (array[j] < min) {
                        index = i;
                        min = array[j];
                        tmp = j;
                    }
                }
                idxs[index]++;
//                System.out.println();
//                System.out.println("tmp=" + tmp);
                aux[idx++] = array[tmp];
            }

            System.arraycopy(aux, 0, array, 0, aux.length);

//            System.out.println("After===================");
//            System.out.println(Arrays.toString(array));
        }
    }

    public BarrierExample(final int size) {
        array = new int[size];
        aux = new int[size];

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < size; ++i)
            array[i] = random.nextInt(0, 2*size);

        int cnt = Runtime.getRuntime().availableProcessors();
        int unit = (int) Math.ceil(size * 1.0 / cnt);
        threads = new Thread[cnt];
        // 闭锁用于统计运行时间
        startGate = new CountDownLatch(1);
        endGate = new CountDownLatch(cnt);

        for (int i = 0; i < cnt; ++i)
            threads[i] = new Thread(
                    new SortThread(
                            i * unit,
                            Math.min(array.length, (i + 1) * unit)
                    ),
                    "Sort-Thread-" + i
            );
        barrier = new CyclicBarrier(cnt, new MergeAction(new int[cnt], unit));
    }

    class SortThread implements Runnable {
        private final int start, end;

        SortThread(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        public void run() {
            try {
                startGate.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Arrays.sort(array, start, end);

            endGate.countDown();

            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }
    }

    public long start() throws InterruptedException {
        for (Thread thread : threads) thread.start();

        long start = System.nanoTime();
        startGate.countDown();
        endGate.await();
        long end = System.nanoTime();

        System.out.println("CyclicBarrier Sort Take Time: " + (end - start) + " nanos");

        return end - start;
    }

    public long justSort() {
        long start = System.nanoTime();
        Arrays.sort(array);
        long end = System.nanoTime();

        System.out.println("Plain Sort Take Time: " + (end - start) + " nanos");

        return end - start;
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        try (
                BufferedWriter writer = new BufferedWriter(new FileWriter("D:/data/sort.txt"));
        ) {
            for (int size = 100; size > 0 && size < (int) 1e9; size *= 2) {
                BarrierExample barrier = new BarrierExample(size);
                long b = barrier.start();

                BarrierExample plain = new BarrierExample(size);
                long p = plain.justSort();

                String result = size + "\t" + b + "\t" + p + "\n";
                writer.write(result);
                writer.flush();
                System.out.println("=====================================");
            }
        }
    }
}
