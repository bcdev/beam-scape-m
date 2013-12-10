package org.esa.beam.util;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.internal.TileImpl;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.SingleBandedOpImage;
import org.junit.Test;

import javax.media.jai.PlanarImage;
import java.awt.*;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 * todo: add comment
 * To change this template use File | Settings | File Templates.
 * Date: 09.12.13
 * Time: 17:35
 *
 * @author olafd
 */
public class ClearPixelStrategyTest {

    @Test
    public void testThatPixelsCanBeInvalid() throws Exception {
        Band bitBand = createBitBand();

        ClearPixelStrategy onlyLandStrategy = new ClearLandPixelStrategy(bitBand);
        boolean[] onlyLandExpectedResults = new boolean[]{true, false, false, false, true, false, false, false,
                false, false, false, false, false, false, false, false};
        ClearPixelStrategy landAndWaterStrategy = new ClearLandAndWaterPixelStrategy(bitBand);
        boolean[] landAndWaterExpectedResults = new boolean[]{true, false, false, false, true, false, false, false,
                true, false, false, false, true, false, false, false};

        int count = 0;
        Tile bitTile = new TileImpl(bitBand, bitBand.getSourceImage().getData());
        onlyLandStrategy.setTile(bitTile);
        landAndWaterStrategy.setTile(bitTile);
        for(int y = 0; y < bitBand.getSceneRasterHeight(); y++) {
            for(int x = 0; x < bitBand.getSceneRasterWidth(); x++) {
                boolean valid = onlyLandStrategy.isValid(x, y);
                assertEquals(onlyLandExpectedResults[count], valid);
                assertEquals(landAndWaterExpectedResults[count], landAndWaterStrategy.isValid(x, y));
                count++;
            }
        }
    }

    private Band createBitBand() throws IOException {
        final int productWidth = 4;
        final int productHeight = 4;
        final Band band = new Band("bitBand", ProductData.TYPE_FLOAT32, productWidth, productHeight);
        ClearPixelStrategyTestImage image = new ClearPixelStrategyTestImage(DataBuffer.TYPE_FLOAT, productWidth,
                                                                            productHeight, new Dimension(productWidth,
                                                                                                         productHeight),
                                                                      null, ResolutionLevel.MAXRES);
        band.setSourceImage(image);
        return band;
    }


    private class ClearPixelStrategyTestImage extends SingleBandedOpImage {

        /**
         * Used to construct an image.
         *
         * @param dataBufferType The data type.
         * @param sourceWidth    The width of the level 0 image.
         * @param sourceHeight   The height of the level 0 image.
         * @param tileSize       The tile size for this image.
         * @param configuration  The configuration map (can be null).
         * @param level          The resolution level.
         */
        protected ClearPixelStrategyTestImage(int dataBufferType, int sourceWidth, int sourceHeight,
                                              Dimension tileSize, Map configuration, ResolutionLevel level) {
            super(dataBufferType, sourceWidth, sourceHeight, tileSize, configuration, level);
        }

        @Override
        protected void computeRect(PlanarImage[] sources, WritableRaster dest, Rectangle destRect) {
            float[] elems = new float[destRect.width * destRect.height];
            int index = 0;
            for (int y = destRect.y; y < destRect.height + destRect.y; y++) {
                for (int x = destRect.x; x < destRect.width + destRect.x; x++) {
                    elems[index] = index++;
                }
            }
            dest.setDataElements(destRect.x, destRect.y, destRect.width, destRect.height, elems);

        }
    }

}
