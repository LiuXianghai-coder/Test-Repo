package org.xhliu.thread.base;

public class ThisEscape {
    public ThisEscape(EventSource source) {
        /*
        	在构造函数中发布 EventListener 时，也会隐式地发布 this，
        	因为在内类 EventListener 中包含了对当前 ThisEscape 对象的引用
        */
        source.registerListener(new EventListener() {
            public void onEvent(Event e) {
                doSomething(e);
            }
        });
    }

    void doSomething(Event e) {}
    interface EventSource {void registerListener(EventListener e);}
    interface EventListener {void onEvent(Event e);}
    interface Event {}

    public static void main(String[] args) {
        EventSource es = new EventSource() {
            private EventListener eventListener;
            public void registerListener(EventListener e) {
                this.eventListener = e;
            }
        };

        new ThisEscape(es);

        System.out.println(es);
    }
}
