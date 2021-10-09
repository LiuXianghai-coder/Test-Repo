import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class JustTest {
    private static final String inFileStr = "/home/lxh/下载/jdk-8u301-linux-x64.tar.gz";
    private static final String outFileStr = "/home/lxh/libcef.so";

    private static long
    directBuffer(
            final int bufferSize
    ) throws IOException {
        long startTime, endTime;
        try (
                FileChannel in = new FileInputStream(inFileStr).getChannel();
                FileChannel out = new FileOutputStream(outFileStr).getChannel();
        ) {
            startTime = System.currentTimeMillis();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            int byteSize;
            while ((byteSize = in.read(buffer) )!= -1) {
                buffer.flip();
                out.write(buffer);
                buffer.compact();
            }

            endTime = System.currentTimeMillis();
        }

        return endTime - startTime;
    }

    private static long
    heapBuffer(
            final int bufferSize
    ) throws IOException {
        long startTime, endTime;
        try (
                FileChannel in = new FileInputStream(inFileStr).getChannel();
                FileChannel out = new FileOutputStream(outFileStr).getChannel();
        ) {
            startTime = System.currentTimeMillis();
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            int byteSize = in.read(buffer);
            while (byteSize != -1) {
                buffer.flip();
                out.write(buffer);
                buffer.compact();

                byteSize = in.read(buffer);
            }
            endTime = System.currentTimeMillis();
        }

        return endTime - startTime;
    }

    private static long
    transferMethod(
            final int bufferSize
    ) throws IOException {
        long startTime, endTime;
        try (
                FileChannel in = new FileInputStream(inFileStr).getChannel();
                FileChannel out = new FileOutputStream(outFileStr).getChannel();
        ) {
            startTime = System.currentTimeMillis();
            in.transferTo(0, in.size(), out);
            endTime = System.currentTimeMillis();
        }

        return endTime - startTime;
    }

    private static long
    buffIo(
            final int bufferSize
    ) throws IOException {
        long startTime, endTime;
        try (
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(inFileStr));
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFileStr));
        ) {
            startTime = System.currentTimeMillis();
            int count;

            while ((count = in.read()) != -1) {
                out.write(count);
            }
            endTime = System.currentTimeMillis();
        }

        return endTime - startTime;
    }

    private static long
    stdIo(
            final int bufferSize
    ) throws IOException {
        long startTime, endTime;
        try (
                InputStream in = new FileInputStream(inFileStr);
                OutputStream out = new FileOutputStream(outFileStr);
        ) {
            startTime = System.currentTimeMillis();
            final byte[] buffer = new byte[bufferSize];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            endTime = System.currentTimeMillis();
        }

        return endTime - startTime;
    }

    public static void main(String[] args) throws IOException {
        File fileIn = new File("/home/lxh/下载/jdk-8u301-linux-x64.tar.gz");
        File fileOut = new File("/home/lxh/libcef.so");

        System.out.println("File Size: " + fileIn.length());

        long startTime, endTime;

        int bufferSizeKb = 4;
        int bufferSize = 1024;

        System.out.println();
        System.out.printf("%10s\t%10s\t%10s\t%10s\t%10s\t%10s%n", "BufferSize", "directBuf", "heapBuf", "transferTo", "buffIo", "stdIo");
        for (int unit = 4; unit <= 1024; unit *= 4) {
            int size = unit * bufferSize;
            System.out.printf("%4dKB\t%8d\t%8d\t%8d\t%8d\t%8d%n",
                    unit,
                    directBuffer(size),
                    heapBuffer(size),
                    transferMethod(size),
                    buffIo(size),
                    stdIo(size)
            );
        }
    }
}

