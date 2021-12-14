package org.xhliu.thread.framework;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduleExecutorServiceExample {
    private static class BeepTask implements Runnable {
        @Override
        public void run() {
            System.out.println("beep!");
        }
    }

    public static void main(String[] args) {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        ScheduledFuture<?> scheduledFuture = service.schedule(
                () -> System.out.println("beep!"),
                2,
                TimeUnit.SECONDS
        );
        service.schedule(
                () -> scheduledFuture.cancel(true),
                10,
                TimeUnit.SECONDS
        );
    }
}
