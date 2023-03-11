package org.xhliu.thread.interrupt;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

public class ReaderThread extends Thread{
    private final static int BUFFER_SIZE = 4 * 1024;

    private final Socket socket;
    private final InputStream inputStream;

    public ReaderThread(Socket socket, InputStream inputStream) {
        this.socket = socket;
        this.inputStream = inputStream;
    }

    @Override
    public void interrupt() {
        try {
            socket.close();
        } catch (IOException e) {
            // ignore ......
        } finally {
            super.interrupt();
        }
    }

    @Override
    public void run() {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                processBuffer(buffer, len);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processBuffer(byte[] buffer, int len) {
    }
}
