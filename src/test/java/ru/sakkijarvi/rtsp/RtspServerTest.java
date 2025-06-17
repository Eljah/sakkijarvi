package ru.sakkijarvi.rtsp;

import org.junit.Test;
import ru.sakkijarvi.videogen.LinearMovelet;
import ru.sakkijarvi.videogen.Movelet;
import ru.sakkijarvi.videogen.VideoGenerator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.ServerSocket;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class RtspServerTest {

    private int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @org.junit.Ignore("requires ffmpeg and network")
    @Test
    public void testRtspMJpeg() throws Exception {
        int frames = 5;
        BufferedImage background = new BufferedImage(200, 200, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D bg = background.createGraphics();
        bg.setColor(Color.BLUE);
        bg.fillRect(0,0,200,200);
        bg.dispose();

        BufferedImage obj = new BufferedImage(50,50,BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D og = obj.createGraphics();
        og.setColor(Color.RED);
        og.fillRect(0,0,50,50);
        og.dispose();

        Movelet mv = new LinearMovelet(new Point(0,0), new Point(100,100), frames);
        VideoGenerator gen = new VideoGenerator(background, obj, mv);
        List<BufferedImage> imgs = gen.generateFrames(frames);

        int port = freePort();
        RtspStreamingServer server = new RtspStreamingServer(port, imgs, false);
        server.start();

        Process ff = new ProcessBuilder("ffmpeg", "-v", "error", "-rtsp_transport", "udp", "-i", "rtsp://127.0.0.1:"+port+"/stream", "-frames:v", "5", "-f", "null", "-")
                .redirectErrorStream(true).start();
        int code = ff.waitFor();
        server.stop();
        assertTrue("ffmpeg exited with code " + code, code == 0);
    }
}
