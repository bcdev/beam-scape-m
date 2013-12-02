package org.esa.beam.operator;

import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;

import java.awt.image.BufferedImage;
import java.io.IOException;

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
        final MultiLevelImage visibilityImage = visibilityBand.getSourceImage();
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
        final BufferedImage newSourceImage = getUpdatedSourceImage(tileWidth, tileHeight, updatedCellValues);
        visibilityBand.setSourceImage(newSourceImage);
        return product;
    }

    private static BufferedImage getUpdatedSourceImage(int tileWidth, int tileHeight, float[][] updatedCellValues) {
        final int productWidth = tileWidth * updatedCellValues.length;
        final int productHeight = tileHeight * updatedCellValues[0].length;
        BufferedImage sourceImage = new BufferedImage(productWidth, productHeight, BufferedImage.TYPE_USHORT_GRAY);
        for (int y = 0; y < updatedCellValues[0].length; y++) {
            for (int x = 0; x < updatedCellValues.length; x++) {
                for (int i = 0; i < tileHeight; i++) {
                    for (int j = 0; j < tileWidth; j++) {
                        sourceImage.getRaster().setSample(x * tileWidth + j,
                                                          y * tileWidth + i, 0, updatedCellValues[x][y]);
                    }
                }
            }
        }
        return sourceImage;
    }

}
