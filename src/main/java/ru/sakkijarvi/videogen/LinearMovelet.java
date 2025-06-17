package ru.sakkijarvi.videogen;

import java.awt.*;

/**
 * Simple linear movement from start to end over given frame count.
 */
public class LinearMovelet implements Movelet {
    private final Point from;
    private final Point to;
    private final int totalFrames;

    public LinearMovelet(Point from, Point to, int totalFrames) {
        this.from = from;
        this.to = to;
        this.totalFrames = totalFrames;
    }

    @Override
    public Point getPosition(int frameIndex, Dimension frameSize, Dimension objectSize) {
        if (frameIndex >= totalFrames) {
            return new Point(to);
        }
        double t = frameIndex / (double) totalFrames;
        int x = (int) Math.round(from.x + (to.x - from.x) * t);
        int y = (int) Math.round(from.y + (to.y - from.y) * t);
        return new Point(x, y);
    }
}
