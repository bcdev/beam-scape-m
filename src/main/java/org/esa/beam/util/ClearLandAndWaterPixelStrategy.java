package org.esa.beam.util;

import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.gpf.Tile;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 09.12.13
 * Time: 17:19
 *
 * @author olafd
 */
public class ClearLandAndWaterPixelStrategy implements ClearPixelStrategy {

    private Tile tile;

    public ClearLandAndWaterPixelStrategy() {
    }

    @Override
    public boolean isValid(int x, int y) {
        int sampleInt = tile.getSampleInt(x, y);
        boolean isInvalid = BitSetter.isFlagSet(sampleInt, ScapeMConstants.CLOUD_INVALID_BIT);
        boolean isCloud = BitSetter.isFlagSet(sampleInt, ScapeMConstants.CLOUD_CERTAIN_BIT);
        return !isCloud && !isInvalid;
    }

    @Override
    public void setTile(Tile tile) {
        this.tile = tile;
    }

}
