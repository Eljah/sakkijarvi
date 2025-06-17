package ru.sakkijarvi.videogen;

import ru.sakkijarvi.TestRtspServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

/**
 * Example of using the high level API to generate a simple moving video
 * and expose it via RTSP.
 */
public class ApiExampleMain {
    public static void main(String[] args) throws Exception {
        BufferedImage background = ImageIO.read(new File("fon.jpeg"));
        BufferedImage object = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = object.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        int frames = 60;
        Movelet movelet = new LinearMovelet(new Point(0, 0), new Point(200, 200), frames);
        VideoGenerator generator = new VideoGenerator(background, object, movelet);
        List<BufferedImage> videoFrames = generator.generateFrames(frames);
        List<byte[]> mjpegs = generator.encodeMJpeg(videoFrames, 0.8f);

        TestRtspServlet.setImage(mjpegs.toArray(new byte[0][]));

        Server server = new Server();
        ServerConnector conn = new ServerConnector(server);
        conn.setPort(8888);
        server.addConnector(conn);

        ServletContextHandler handler = new ServletContextHandler();
        handler.addServlet(new ServletHolder(TestRtspServlet.class), "/video/*");
        server.setHandler(handler);

        server.start();
        server.join();
    }
}
