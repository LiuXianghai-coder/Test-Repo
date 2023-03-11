package org.xhliu.thread.aqs;

import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RWLockExample {

}

class CachedData {
    Object data;
    volatile boolean cacheValid;
    final DataAccess access;
    final Order order;
    final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    CachedData(DataAccess access, Order order) {
        this.access = access;
        this.order = order;
    }

    void processCachedData() {
        rwl.readLock().lock();
        if (!cacheValid) {
            // Must release read lock before acquiring write lock
            rwl.readLock().unlock();
            rwl.writeLock().lock();
            try {
                // Recheck state because another thread might have
                // acquired write lock and changed state before we did.
                if (!cacheValid) {
                    data = access.queryData();
                    cacheValid = true;
                }
                // Downgrade by acquiring read lock before releasing write lock
                rwl.readLock().lock();
            } finally {
                rwl.writeLock().unlock(); // Unlock write, still hold read
            }
        }

        try {
            order.useData(data);
        } finally {
            rwl.readLock().unlock();
        }
    }

    interface DataAccess {
        Object queryData();
    }

    interface Order {
        void useData(Object data);
    }
}
