package org.xhliu.thread.base;

public class SynchronizedExample {
    final Object object = new Object();

    int cnt = 0;

    public void plus() {
        synchronized (object) {
            cnt++;
        }
    }
}
