package org.xhliu.thread.framework;

import java.util.concurrent.*;

public class ForkJoinExample {
    static class CountTask extends RecursiveTask<Long> {
        private static final int THRESHOLD = Runtime.getRuntime().availableProcessors();  // 阈值
        private static volatile int unit = -1;
        private final int start;
        private final int end;

        public CountTask(int start, int end) {
            this.start = start;
            this.end = end;
        }

        @Override
        protected Long compute() {
            long sum = 0;
            boolean canCompute = (end - start) <= unit;

            if (canCompute) {
                // 如果任务足够小就计算任务
                for (int i = start; i <= end; i++) {
                    sum += i;
                }
            } else {
                // 如果任务大于阈值，就分裂成多个子任务计算
                CountTask[] tasks = new CountTask[THRESHOLD];
                long[] res = new long[THRESHOLD];
                // 第一次访问时设置 unit
                if (unit < 0) unit = (int) Math.ceil((end - start) * 1.0 / THRESHOLD);
                for (int i = 0; i < THRESHOLD; ++i) {
                    if (i == 0)
                        tasks[i] = new CountTask(start, start + (i + 1) * unit);
                    else
                        tasks[i] = new CountTask(start + i * unit + 1, Math.min(start + (i + 1) * unit, end));
                }

                // 启动所有的子任务
                for (CountTask task : tasks) task.fork();

                // 等待子任务执行完，并得到其结果
                for (int i = 0; i < res.length; ++i) {
                    res[i] = tasks[i].join();
                }

                // 合并子任务
                for (long val : res) sum += val;
            }

            return sum;
        }
    }

    static long cal(int start, int end) {
        long ans = 0L;
        for (int i = start; i <= end; ++i)
            ans += i;
        return ans;
    }

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        long start, end;

        int lo = 1, hi = (int) 1e9;

        start = System.nanoTime();
        ForkJoinPool pool = new ForkJoinPool();
        CountTask task = new CountTask(lo, hi);
        Future<Long> res = pool.submit(task);
        if (task.isCompletedAbnormally()) {
            task.getException().printStackTrace();
        }
        System.out.println(res.get());
        end = System.nanoTime();
        System.out.println("Take time: " + (end - start) + " nanos");

        System.out.println("============================");

        start = System.nanoTime();
        long cal = cal(lo, hi);
        end = System.nanoTime();
        System.out.println("Take time: " + (end - start) + " nanos");
    }
}
