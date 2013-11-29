package org.esa.beam.operator;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;

public class ScapeMGapFillTest {

    @Test
    public void testGetMinimumDistanceToEdge() {
        assertEquals(0, ScapeMGapFill.getMinimumDistanceToEdge(0, 0, 5, 5), 1e-8);
        assertEquals(0, ScapeMGapFill.getMinimumDistanceToEdge(0, 1, 5, 5), 1e-8);
        assertEquals(0, ScapeMGapFill.getMinimumDistanceToEdge(1, 0, 5, 5), 1e-8);
        assertEquals(1, ScapeMGapFill.getMinimumDistanceToEdge(1, 1, 5, 5), 1e-8);
        assertEquals(0, ScapeMGapFill.getMinimumDistanceToEdge(4, 4, 5, 5), 1e-8);
        assertEquals(0, ScapeMGapFill.getMinimumDistanceToEdge(3, 4, 5, 5), 1e-8);
        assertEquals(0, ScapeMGapFill.getMinimumDistanceToEdge(4, 3, 5, 5), 1e-8);
        assertEquals(1, ScapeMGapFill.getMinimumDistanceToEdge(3, 3, 5, 5), 1e-8);
        assertEquals(-1, ScapeMGapFill.getMinimumDistanceToEdge(-1, 3, 5, 5), 1e-8);
        assertEquals(-1, ScapeMGapFill.getMinimumDistanceToEdge(3, -1, 5, 5), 1e-8);
        assertEquals(-1, ScapeMGapFill.getMinimumDistanceToEdge(3, 5, 5, 5), 1e-8);
        assertEquals(-1, ScapeMGapFill.getMinimumDistanceToEdge(5, 3, 5, 5), 1e-8);
        assertEquals(2, ScapeMGapFill.getMinimumDistanceToEdge(2, 2, 5, 5), 1e-8);
        assertEquals(2, ScapeMGapFill.getMinimumDistanceToEdge(3, 3, 6, 6), 1e-8);
        assertEquals(3, ScapeMGapFill.getMinimumDistanceToEdge(3, 3, 7, 7), 1e-8);
    }

    @Test
    public void testScapeMInterpolateOverRegion() {
        float[][] cellSamples = {{1, 2, 3}, {4, -1, 6}, {7, 8, 9}};
        assertEquals(5, ScapeMGapFill.interpolateOverRegion(cellSamples, 1, 1, 1), 1e-8);

        cellSamples = new float[][]{{1, 2, 3}, {-1, -1, 6}, {6, 8, 9}};
        assertEquals(5, ScapeMGapFill.interpolateOverRegion(cellSamples, 1, 1, 1), 1e-8);

        cellSamples = new float[][]{{1, 2, 3, 4, 5}, {6, 7, 8, 9, 10}, {11, 12, -1, 14, 15},
                {16, 17, 18, 19, 20}, {21, 22, 23, 24, 25}};
        assertEquals(13, ScapeMGapFill.interpolateOverRegion(cellSamples, 2, 2, 2), 1e-8);

        cellSamples = new float[][]{{1, 2, 3, 4, 5}, {6, 7, 8, 9, 10}, {11, 12, 13, 14, 15},
                {16, 17, 18, 19, 20}, {21, 22, 23, 24, 25}};
        for(int i = 1; i < 4; i++) {
            for(int j = 1; j < 4; j++) {
                final float temp = cellSamples[i][j];
                cellSamples[i][j] = -1;
                assertEquals(temp, ScapeMGapFill.interpolateOverRegion(cellSamples, i, j, 1), 1e-8);
                cellSamples[i][j] = temp;
            }
        }

        cellSamples = new float[][]{{1, 2, 3, 4, 5}, {6, 7, -1, 9, 10}, {11, 12, -1, 14, 15},
                {16, 17, 18, 19, 20}, {21, 22, 18, 24, 25}};
        assertEquals(13, ScapeMGapFill.interpolateOverRegion(cellSamples, 2, 2, 2), 1e-8);
    }



    @Test
    @Ignore
    public void testScapeMGapFill() throws IOException {
//        final int pixelsPerCell = ScapeMOp.RR_PIXELS_PER_CELL;
//        Product productToBeFilled = createUnFilledDummyRRProduct(pixelsPerCell);
        final int pixelsPerCell = 1;
        Product productToBeFilled = createUnFilledDummyRRProduct(pixelsPerCell);

        Product filledProduct = ScapeMGapFill.gapFill(productToBeFilled);

        assertNotNull(filledProduct);
        int width = filledProduct.getSceneRasterWidth();
        int height = filledProduct.getSceneRasterHeight();
        final Band filledProductBand = filledProduct.getBand(ScapeMVisibilityOp.VISIBILITY_BAND_NAME);
        for (int y = 0; y < height; y++) {
            double groundValue = 3 * (y / pixelsPerCell);
            for (int x = 0; x < width; x++) {
                double value = x / pixelsPerCell + groundValue + 1;
                if (value == 5) {
                    value = 0;
                }
                assertEquals(value, filledProductBand.getSampleFloat(x, y), 1e-8);
            }
        }
    }

    private Product createUnFilledDummyRRProduct(int pixelsPerCell) throws IOException {
        final int productWidthAndHeight = pixelsPerCell * 3;
        Product product = new Product("dummyRRProduct", "doesntMatter", productWidthAndHeight, productWidthAndHeight);
        product.setPreferredTileSize(pixelsPerCell, pixelsPerCell);
        final String bandName = ScapeMVisibilityOp.VISIBILITY_BAND_NAME;
        final Band band = new Band(bandName, ProductData.TYPE_INT32, productWidthAndHeight, productWidthAndHeight);
        product.addBand(band);
        double[] bandValues = new double[]{1, 2, 3, 4, -1, 6, 7, 8, 9};
//        band.setSourceImage(createSourceImage(pixelsPerCell, productWidthAndHeight, productWidthAndHeight));
        band.writePixels(0, 0, productWidthAndHeight, productWidthAndHeight, bandValues);
        return product;
    }

    private Product createUnFilledDummyRRProduct2(int pixelsPerCell) {
        final int productWidthAndHeight = pixelsPerCell * 3;
        Product product = new Product("dummyRRProduct", "doesntMatter", productWidthAndHeight, productWidthAndHeight);
        product.setPreferredTileSize(pixelsPerCell, pixelsPerCell);
        final String bandName = ScapeMVisibilityOp.VISIBILITY_BAND_NAME;
        final Band band = new Band(bandName, ProductData.TYPE_INT32, productWidthAndHeight, productWidthAndHeight);
        band.setSourceImage(createSourceImage(pixelsPerCell, productWidthAndHeight, productWidthAndHeight));
        product.addBand(band);
        return product;
    }

    private static BufferedImage createSourceImage(int pixelsPerCell, int srcW, int srcH) {
        BufferedImage sourceImage = new BufferedImage(srcW, srcH, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < srcH; y++) {
            double groundValue = 3 * (y / pixelsPerCell);
            for (int x = 0; x < srcW; x++) {
                double value = x / pixelsPerCell + groundValue + 1;
                if(value == 5.0) {
                    value = -1.0;
                }
                sourceImage.getRaster().setSample(x, y, 0, value);
            }
        }
        return sourceImage;
    }


}