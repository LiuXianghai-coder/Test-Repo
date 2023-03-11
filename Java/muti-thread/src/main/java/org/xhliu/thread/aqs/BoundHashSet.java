package org.xhliu.thread.aqs;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Semaphore;

public class BoundHashSet<T> {
    private final Set<T> set;
    private final Semaphore semaphore;

    public BoundHashSet(final int bound) {
        this.set = Collections.synchronizedSet(new HashSet<>());
        this.semaphore = new Semaphore(bound);
    }

    public boolean add(T o) throws InterruptedException {
        semaphore.acquire();
        boolean wasAdded = false;
        try {
            wasAdded = set.add(o);
            return wasAdded;
        } finally {
            if (!wasAdded) semaphore.release();
        }
    }

    public boolean remove(Object o) {
        boolean wasRemoved = set.remove(o);
        if (wasRemoved)
            semaphore.release();
        return wasRemoved;
    }

    public static void main(String[] args) throws InterruptedException {
        BoundHashSet<String> set = new BoundHashSet<>(3);
    }
}
