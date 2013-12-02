package org.esa.beam.operator;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
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
        double noDataValue = -1;
        float[][] cellSamples = {{1, 2, 3}, {4, -1, 6}, {7, 8, 9}};
        assertEquals(5, ScapeMGapFill.interpolateOverRegion(cellSamples, 1, 1, 1, noDataValue), 1e-8);

        cellSamples = new float[][]{{1, 2, 3}, {-1, -1, 6}, {6, 8, 9}};
        assertEquals(5, ScapeMGapFill.interpolateOverRegion(cellSamples, 1, 1, 1, noDataValue), 1e-8);

        cellSamples = new float[][]{{1, 2, 3, 4, 5}, {6, 7, 8, 9, 10}, {11, 12, -1, 14, 15},
                {16, 17, 18, 19, 20}, {21, 22, 23, 24, 25}};
        assertEquals(13, ScapeMGapFill.interpolateOverRegion(cellSamples, 2, 2, 2, noDataValue), 1e-8);

        cellSamples = new float[][]{{1, 2, 3, 4, 5}, {6, 7, 8, 9, 10}, {11, 12, 13, 14, 15},
                {16, 17, 18, 19, 20}, {21, 22, 23, 24, 25}};
        for (int i = 1; i < 4; i++) {
            for (int j = 1; j < 4; j++) {
                final float temp = cellSamples[i][j];
                cellSamples[i][j] = -1;
                assertEquals(temp, ScapeMGapFill.interpolateOverRegion(cellSamples, i, j, 1, noDataValue), 1e-8);
                cellSamples[i][j] = temp;
            }
        }

        cellSamples = new float[][]{{1, 2, 3, 4, 5}, {6, 7, -1, 9, 10}, {11, 12, -1, 14, 15},
                {16, 17, 18, 19, 20}, {21, 22, 18, 24, 25}};
        assertEquals(13, ScapeMGapFill.interpolateOverRegion(cellSamples, 2, 2, 2, noDataValue), 1e-8);
    }

    @Test
    public void testScapeMInterpolationAtCorner() {
        double noDataValue = -1;
        float[][] cellSamples = {{-1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
        assertEquals(3.4, ScapeMGapFill.interpolateAtCornerOrBorder(3, 3, cellSamples, 0, 0, noDataValue), 1e-7);
        cellSamples = new float[][]{{1, 2, 3}, {4, 5, 6}, {7, 8, -1}};
        assertEquals(6.6, ScapeMGapFill.interpolateAtCornerOrBorder(3, 3, cellSamples, 2, 2, noDataValue), 1e-7);
    }

    @Test
    public void testScapeMInterpolationAtBorder() {
        double noDataValue = -1;
        float[][] cellSamples = {{1, -1, 3}, {4, 5, 6}, {7, 8, 9}};
        assertEquals((float) 23 / 7, ScapeMGapFill.interpolateAtCornerOrBorder(3, 3, cellSamples, 0, 1, noDataValue), 1e-8);
        cellSamples = new float[][]{{1, 2, 3}, {4, 5, -1}, {7, 8, 9}};
        assertEquals((float) 39 / 7, ScapeMGapFill.interpolateAtCornerOrBorder(3, 3, cellSamples, 1, 2, noDataValue), 1e-8);
    }

    @Test
    public void testScapeMGapFill() throws IOException {
        final int pixelsPerCell = ScapeMOp.RR_PIXELS_PER_CELL;
        Product productToBeFilled = createUnFilledDummyRRProduct2(pixelsPerCell);

        Product filledProduct = ScapeMGapFill.gapFill(productToBeFilled);

        assertNotNull(filledProduct);
        int width = filledProduct.getSceneRasterWidth();
        assertEquals(productToBeFilled.getSceneRasterWidth(), width);
        int height = filledProduct.getSceneRasterHeight();
        assertEquals(productToBeFilled.getSceneRasterHeight(), height);
        final Band filledProductBand = filledProduct.getBand(ScapeMVisibilityOp.VISIBILITY_BAND_NAME);
        assertNotNull(filledProductBand);
        for (int y = 0; y < height; y++) {
            double groundValue = 3 * (y / pixelsPerCell);
            for (int x = 0; x < width; x++) {
                double value = x / pixelsPerCell + groundValue + 1;
                assertEquals(value, filledProductBand.getSampleFloat(x, y), 1e-8);
            }
        }
    }

    private Product createUnFilledDummyRRProduct2(int pixelsPerCell) {
        final int productWidth = pixelsPerCell * 3;
        final int productHeight = pixelsPerCell * 4;
        Product product = new Product("dummyRRProduct", "doesntMatter", productWidth, productHeight);
        product.setPreferredTileSize(pixelsPerCell, pixelsPerCell);
        final String bandName = ScapeMVisibilityOp.VISIBILITY_BAND_NAME;
        final Band band = new Band(bandName, ProductData.TYPE_INT32, productWidth, productHeight);
        band.setSourceImage(createSourceImage(pixelsPerCell, productWidth, productHeight));
        band.setNoDataValue(1000.0);
        product.addBand(band);
        return product;
    }

    private static BufferedImage createSourceImage(int pixelsPerCell, int srcW, int srcH) {
        BufferedImage sourceImage = new BufferedImage(srcW, srcH, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < srcH; y++) {
            double groundValue = 3 * (y / pixelsPerCell);
            for (int x = 0; x < srcW; x++) {
                double value = x / pixelsPerCell + groundValue + 1;
                if (value == 5.0) {
                    value = 1000.0;
                }
                sourceImage.getRaster().setSample(x, y, 0, value);
            }
        }
        return sourceImage;
    }


}