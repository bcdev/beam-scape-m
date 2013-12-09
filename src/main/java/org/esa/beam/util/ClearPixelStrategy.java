package org.esa.beam.util;

import org.esa.beam.framework.gpf.Tile;

import java.awt.*;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 09.12.13
 * Time: 17:18
 *
 * @author olafd
 */
public interface ClearPixelStrategy {

    boolean isValid(int x, int y);

    void setTile(Tile tile);

}
