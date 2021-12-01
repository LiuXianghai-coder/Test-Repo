package org.xhliu.thread.base;

import java.util.Random;

public class StateSafeClass {
    public long random(long seed) {
        Random random = new Random(seed);
        return random.nextLong();
    }
}
