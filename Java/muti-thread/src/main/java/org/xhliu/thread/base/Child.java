package org.xhliu.thread.base;

public class Child extends Parent {
    public synchronized void childDo() {
        // child do something.....
        super.parentDo();
    }
}
