package com.maven.spider;

import lombok.SneakyThrows;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author xhliu2
 * @create 2021-08-19 14:05
 **/
public class JustTest {
    private final static String url = "jdbc:postgresql://39.99.129.90:5432/lxh_db";
    private final static String userName = "postgres";
    private final static String password = "17358870357yi";

    private final static String path = "D:/data/img/";

    private final static String chapter = "chapter-";

    private final static int bufferSizeMB = 10;

    private final static int bufferSize = bufferSizeMB * 1024 * 1024;

    private final static String USER_AGENT = "User-Agent";

    private final static String USER_AGENT_VAL = "User-Agent";

    private final static Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 10809));

    private final static String getAllSQL = "SELECT * FROM manhua_img LIMIT ? OFFSET ?";

    private final static String countSQL = "SELECT COUNT(*) AS num FROM manhua_img";

    private final static AtomicLong count = new AtomicLong(0);

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            new Thread(new TestThreadRun(0, 405)).start();
            new Thread(new TestThreadRun(405, 405)).start();
            new Thread(new TestThreadRun(810, 405)).start();
            new Thread(new TestThreadRun(1205, 406)).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class TestThreadRun implements Runnable {
        private final int start, num;

        public TestThreadRun(int start, int num) {
            this.start = start;
            this.num = num;
        }

        @SneakyThrows
        @Override
        public void run() {
            JustTest test = new JustTest();
            test.getData(this.start, this.num);
        }
    }

    private void getData(int start, int num) throws Throwable {
        ResultSet resultSet = null;
        try (
                Connection connection = DriverManager.getConnection(url, userName, password);
                PreparedStatement preparedStatement = connection.prepareStatement(getAllSQL);
        ) {
            preparedStatement.setInt(1, num);
            preparedStatement.setInt(2, start);
            resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                ImgHref imgHref = new ImgHref();
                imgHref.setImgId(resultSet.getLong("img_id"));
                imgHref.setChapterId(resultSet.getInt("chapter_id"));
                imgHref.setChapterImgId(resultSet.getInt("chapter_img_id"));
                imgHref.setImgHref(resultSet.getString("img_href"));

                getImg(imgHref);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (null != resultSet)
                resultSet.close();
        }
    }

    private void getImg(ImgHref imgHref) throws Throwable {
        File file = new File(path + chapter + imgHref.getChapterId());
        if (!file.exists()) {
            if (file.mkdirs()) {
                // System.out.println("mkdir success");
                /* TODO */
            }
        }

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(bufferSize);
        String fileName = file.getPath() + "/" + imgHref.getChapterImgId() + ".png";

        file = new File(fileName);
        if (!file.exists()) {
            if (file.createNewFile()) {
                // System.out.println("create file success.");
                /* TODO */
            }
        }

        System.out.println("save href: " + imgHref.getImgHref() + "\tcount: " + count.getAndIncrement());
        HttpURLConnection connection = (HttpURLConnection) new URL(imgHref.getImgHref()).openConnection(proxy);
        connection.setRequestProperty(USER_AGENT, USER_AGENT_VAL);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        try (
                ReadableByteChannel in = Channels.newChannel(connection.getInputStream());
                FileChannel out = new FileOutputStream(fileName).getChannel();
        ) {
            out.transferFrom(in, byteBuffer.position(), byteBuffer.limit());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveData() {
        String sql = "INSERT INTO comic_dir(comic_name, chapter_name, img_num) VALUES(?, ?, ?)";
        try (
                Connection connection = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/manhua", userName, password);
                PreparedStatement preparedStatement = connection.prepareStatement(sql);
        ) {
            File[] chapters = new File("D:/data/img").listFiles();
            ConcurrentHashMap<String, Integer> chapterMap = new ConcurrentHashMap<>();

            assert chapters != null;
            for (File chapter : chapters) {
                chapterMap.putIfAbsent(chapter.getName(),
                        Objects.requireNonNull(chapter.listFiles()).length);
            }

            for (String chapter : chapterMap.keySet()) {
                for (int i = 1; i <= chapterMap.get(chapter); ++i) {
                    Comic comic = new Comic();
                    comic.setChapter(chapter);
                    comic.setComicName("Secret-Class");
                    comic.setChapterNumber(i);

                    preparedStatement.setObject(1, comic.getComicName());
                    preparedStatement.setObject(2, comic.getChapter());
                    preparedStatement.setObject(3, comic.getChapterNumber());

                    preparedStatement.execute();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Throwable {
        JustTest justTest = new JustTest();
//        justTest.start();
        saveData();
    }
}