package ru.sakkijarvi.videogen;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * High level helper to generate a synthetic video sequence.
 */
public class VideoGenerator {
    private final BufferedImage background;
    private final BufferedImage object;
    private final Movelet movelet;

    public VideoGenerator(BufferedImage background, BufferedImage object, Movelet movelet) {
        this.background = background;
        this.object = object;
        this.movelet = movelet;
    }

    /**
     * Generates frames with moving object.
     */
    public List<BufferedImage> generateFrames(int frameCount) {
        List<BufferedImage> frames = new ArrayList<>();
        Dimension frameSize = new Dimension(background.getWidth(), background.getHeight());
        Dimension objSize = new Dimension(object.getWidth(), object.getHeight());
        for (int i = 0; i < frameCount; i++) {
            BufferedImage frame = new BufferedImage(frameSize.width, frameSize.height, background.getType());
            Graphics2D g = frame.createGraphics();
            g.drawImage(background, 0, 0, null);
            Point p = movelet.getPosition(i, frameSize, objSize);
            g.drawImage(object, p.x, p.y, null);
            g.dispose();
            frames.add(frame);
        }
        return frames;
    }

    /**
     * Encodes frames into MJPEG byte array list.
     */
    public List<byte[]> encodeMJpeg(List<BufferedImage> frames, float quality) throws IOException {
        List<byte[]> result = new ArrayList<>();
        for (BufferedImage f : frames) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(f, "jpg", baos);
            result.add(baos.toByteArray());
        }
        return result;
    }

    /**
     * Encodes frames into an H.264 MP4 file using the pure Java JCodec library.
     */
    public File encodeH264(File output, List<BufferedImage> frames) throws IOException {
        org.jcodec.api.awt.AWTSequenceEncoder enc = org.jcodec.api.awt.AWTSequenceEncoder.createSequenceEncoder(output, 25);
        for (BufferedImage f : frames) {
            enc.encodeImage(f);
        }
        enc.finish();
        return output;
    }
}
