package org.esa.beam.operator;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;

import java.io.IOException;
import java.util.Arrays;

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
                                               int x, int y, int neighboringDistance) {
        float meanValue = 0;
        int validNeighboringCellsCounter = 0;
        for (int i = -neighboringDistance; i <= neighboringDistance; i++) {
            for (int j = -neighboringDistance; j <= neighboringDistance; j++) {
                if (cellSamples[x + i][y + j] != -1) {
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
    static float interpolateAtCornerOrBorder(int numberOfCellColumns, int numberOfCellRows, float[][] cellSamples, int x, int y) {
        float mean = 0;
        int validCellsCounter = 0;
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                final int xAssign = x + i;
                final int yAssign = y + j;
                final int minimumDistanceToEdgeAssign
                        = getMinimumDistanceToEdge(xAssign, yAssign, numberOfCellColumns, numberOfCellRows);
                if (minimumDistanceToEdgeAssign >= 0) {
                    if (cellSamples[xAssign][yAssign] != -1) {
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
        final int tileWidth = (int) product.getPreferredTileSize().getWidth();
        final int tileHeight = (int) product.getPreferredTileSize().getHeight();
        final int numberOfCellColumns = product.getSceneRasterWidth() / tileWidth;
        final int numberOfCellRows = product.getSceneRasterHeight() / tileHeight;
        float[][] cellSamples = new float[numberOfCellColumns][numberOfCellRows];
        float areaMean = 0;
        int numberOfValidCells = 0;
        for (int y = 0; y < numberOfCellRows; y++) {
            for (int x = 0; x < numberOfCellColumns; x++) {
                final float cellValue = visibilityBand.getSampleFloat(x * tileWidth, y * tileHeight);
                cellSamples[x][y] = cellValue;
                if (cellValue != -1) {
                    areaMean += cellValue;
                    numberOfValidCells++;
                }
            }
        }
        areaMean /= numberOfValidCells;
        for (int y = 0; y < numberOfCellRows; y++) {
            for (int x = 0; x < numberOfCellColumns; x++) {
                float cellSample = cellSamples[x][y];
                if (cellSample == -1) {
                    float interpolationValue = 0;
                    final int minimumDistanceToEdge = getMinimumDistanceToEdge(x, y,
                                                                               numberOfCellColumns, numberOfCellRows);
                    if (minimumDistanceToEdge >= 2) {
                        interpolationValue = interpolateOverRegion(cellSamples, x, y, 2);
                    } else if (minimumDistanceToEdge == 1) {
                        interpolationValue = interpolateOverRegion(cellSamples, x, y, 1);
                    } else {
                        interpolationValue = interpolateAtCornerOrBorder(numberOfCellColumns, numberOfCellRows,
                                                                         cellSamples, x, y);
                    }
                    if (interpolationValue == 0 && minimumDistanceToEdge >= 3) {
                        interpolationValue = interpolateOverRegion(cellSamples, x, y, 3);
                    }
                    if (interpolationValue == 0) {
                        interpolationValue = areaMean;
                    }
                    float[] interpolationArray = new float[tileHeight * tileWidth];
                    Arrays.fill(interpolationArray, interpolationValue);
                    final ProductData productData = ProductData.createInstance(interpolationArray);
                    visibilityBand.writeRasterData(x * tileWidth, y * tileHeight, tileWidth, tileHeight,
                                                        productData, null);
                }
            }
        }
        return product;
    }

}
