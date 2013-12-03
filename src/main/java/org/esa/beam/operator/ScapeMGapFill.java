package org.esa.beam.operator;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.jai.ResolutionLevel;
import org.esa.beam.jai.SingleBandedOpImage;

import javax.media.jai.PlanarImage;
import java.awt.*;
import java.awt.image.DataBuffer;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.util.Map;

public class ScapeMGapFill {

    /* package local for testing*/
    static int getMinimumDistanceToEdge(int x, int y, int numberOfCellColumns, int numberOfCellRows) {
        return Math.min(x,
                        Math.min(y,
                                 Math.min(numberOfCellColumns - 1 - x,
                                          numberOfCellRows - 1 - y)));
    }

    /* package local for testing*/
    static float interpolateOverRegion(float[][] cellSamples,
                                       int x, int y, int neighboringDistance, double noDataValue) {
        float meanValue = 0;
        int validNeighboringCellsCounter = 0;
        for (int i = -neighboringDistance; i <= neighboringDistance; i++) {
            for (int j = -neighboringDistance; j <= neighboringDistance; j++) {
                if (cellSamples[x + i][y + j] != noDataValue) {
                    meanValue += cellSamples[x + i][y + j];
                    validNeighboringCellsCounter++;
                }
            }
        }
        if (validNeighboringCellsCounter > 0) {
            meanValue /= validNeighboringCellsCounter;
        }
        return meanValue;
    }

    /* package local for testing*/
    static float interpolateAtCornerOrBorder(int numberOfCellColumns, int numberOfCellRows, float[][] cellSamples,
                                             int x, int y, double noDataValue) {
        float mean = 0;
        int validCellsCounter = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                final int xAssign = x + i;
                final int yAssign = y + j;
                final int minimumDistanceToEdgeAssign
                        = getMinimumDistanceToEdge(xAssign, yAssign, numberOfCellColumns, numberOfCellRows);
                if (minimumDistanceToEdgeAssign >= 0) {
                    if (cellSamples[xAssign][yAssign] != noDataValue) {
                        mean += cellSamples[xAssign][yAssign];
                        validCellsCounter++;
                        if (minimumDistanceToEdgeAssign == 0 && (xAssign == x || yAssign == y)) {
                            mean += cellSamples[xAssign][yAssign];
                            validCellsCounter++;
                        }
                    }
                }
            }
        }
        if (validCellsCounter > 0) {
            mean /= validCellsCounter;
        }
        return mean;
    }

    public static Product gapFill(Product product) throws IOException {
        final Band visibilityBand = product.getBand(ScapeMVisibilityOp.VISIBILITY_BAND_NAME);
        final double noDataValue = visibilityBand.getNoDataValue();
        final int tileWidth = (int) product.getPreferredTileSize().getWidth();
        final int tileHeight = (int) product.getPreferredTileSize().getHeight();
        final int numberOfCellColumns = (int) Math.ceil(product.getSceneRasterWidth() * 1.0 / tileWidth);
        final int numberOfCellRows = (int) Math.ceil(product.getSceneRasterHeight() * 1.0 / tileHeight);
        float[][] cellSamples = new float[numberOfCellColumns][numberOfCellRows];
        float areaMean = 0;
        int numberOfValidCells = 0;
        for (int y = 0; y < numberOfCellRows; y++) {
            for (int x = 0; x < numberOfCellColumns; x++) {
                final float cellValue = visibilityBand.getSampleFloat(x * tileWidth, y * tileHeight);
                if (Double.isNaN(cellValue)) {
                    cellSamples[x][y] = (float) noDataValue;
                } else {
                    cellSamples[x][y] = cellValue;
                }
                if (cellValue != noDataValue) {
                    areaMean += cellValue;
                    numberOfValidCells++;
                }
            }
        }
        float[][] updatedCellValues = new float[numberOfCellColumns][numberOfCellRows];
        areaMean /= numberOfValidCells;
        for (int y = 0; y < numberOfCellRows; y++) {
            for (int x = 0; x < numberOfCellColumns; x++) {
                float cellSample = cellSamples[x][y];
                if (cellSample == noDataValue) {
                    float interpolationValue = 0;
                    final int minimumDistanceToEdge = getMinimumDistanceToEdge(x, y,
                                                                               numberOfCellColumns, numberOfCellRows);
                    if (minimumDistanceToEdge >= 2) {
                        interpolationValue = interpolateOverRegion(cellSamples, x, y, 2, noDataValue);
                    } else if (minimumDistanceToEdge == 1) {
                        interpolationValue = interpolateOverRegion(cellSamples, x, y, 1, noDataValue);
                    } else {
                        interpolationValue = interpolateAtCornerOrBorder(numberOfCellColumns, numberOfCellRows,
                                                                         cellSamples, x, y, noDataValue);
                    }
                    if (interpolationValue == 0 && minimumDistanceToEdge >= 3) {
                        interpolationValue = interpolateOverRegion(cellSamples, x, y, 3, noDataValue);
                    }
                    if (interpolationValue == 0) {
                        interpolationValue = areaMean;
                    }
                    updatedCellValues[x][y] = interpolationValue;
                } else {
                    updatedCellValues[x][y] = cellSamples[x][y];
                }
            }
        }
        ScapeMImage image = new ScapeMImage(DataBuffer.TYPE_FLOAT, product.getSceneRasterWidth(), product.getSceneRasterHeight(),
                                                  new Dimension(tileWidth, tileHeight), null, ResolutionLevel.MAXRES,
                                                  updatedCellValues);
        visibilityBand.setSourceImage(image);
        return product;
    }


    private static class ScapeMImage extends SingleBandedOpImage {
        private final int tileHeight;
        private final int tileWidth;
        private final float[][] updatedCellValues;

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
        protected ScapeMImage(int dataBufferType, int sourceWidth, int sourceHeight, Dimension tileSize,
                              Map configuration, ResolutionLevel level, float[][] updatedCellValues) {
            super(dataBufferType, sourceWidth, sourceHeight, tileSize, configuration, level);
            tileHeight = tileSize.height;
            tileWidth = tileSize.width;
            this.updatedCellValues = updatedCellValues;
        }

        @Override
        protected void computeRect(PlanarImage[] sources, WritableRaster dest, Rectangle destRect) {
            float[] elems = new float[destRect.width * destRect.height];
            int index = 0;
            for (int y = destRect.y; y < destRect.height + destRect.y; y++) {
                int yCellIndex = y / tileHeight;
                for (int x = destRect.x; x < destRect.width + destRect.x; x++) {
                    int xCellIndex = x / tileWidth;
                    float value = updatedCellValues[xCellIndex][yCellIndex];
                    elems[index++] = value;
                }
            }
            dest.setDataElements(destRect.x, destRect.y, destRect.width, destRect.height, elems);

        }
    }

}
