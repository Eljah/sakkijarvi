package ru.sakkijarvi.videogen;

import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class VideoGeneratorTest {

    @org.junit.Ignore("requires ffmpeg and gstreamer")
    @Test
    public void testGeneratedVideoAcceptedByTools() throws Exception {
        BufferedImage background = ImageIO.read(new File("fon.jpeg"));
        BufferedImage object = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = object.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 50, 50);
        g.dispose();

        int frames = 10;
        Movelet movelet = new LinearMovelet(new Point(0, 0), new Point(100, 100), frames);
        VideoGenerator generator = new VideoGenerator(background, object, movelet);
        List<BufferedImage> imgs = generator.generateFrames(frames);
        File out = File.createTempFile("test", ".mp4");
        generator.encodeH264(out, imgs);

        Process ffmpeg = new ProcessBuilder("ffmpeg", "-v", "error", "-i", out.getAbsolutePath(), "-f", "null", "-")
                .redirectErrorStream(true).start();
        int ffmpegCode = ffmpeg.waitFor();
        assertTrue("ffmpeg exited with code " + ffmpegCode, ffmpegCode == 0);

        Process gst = new ProcessBuilder("gst-launch-1.0", "filesrc", "location=" + out.getAbsolutePath(), "!", "decodebin", "!", "fakesink")
                .redirectErrorStream(true).start();
        int gstCode = gst.waitFor();
        assertTrue("gstreamer exited with code " + gstCode, gstCode == 0);
    }
}
