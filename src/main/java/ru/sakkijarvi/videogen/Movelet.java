package ru.sakkijarvi.videogen;

import java.awt.*;

/**
 * Describes movement of an object on background.
 */
public interface Movelet {
    /**
     * Returns top-left position of the object for the given frame index.
     */
    Point getPosition(int frameIndex, Dimension frameSize, Dimension objectSize);
}
