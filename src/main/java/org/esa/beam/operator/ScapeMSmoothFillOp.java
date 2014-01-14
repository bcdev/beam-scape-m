package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.RectangleExtender;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Operator for filling right and lower edges of smoothed product.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM.smooth.fill", version = "1.0-SNAPSHOT",
                  authors = "Tonio Fincke, Olaf Danne",
                  copyright = "(c) 2013 Brockmann Consult",
                  internal = true,
                  description = "Fills right and lower edges of smoothed product.")
public class ScapeMSmoothFillOp extends ScapeMMerisBasisOp implements Constants {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @SourceProduct(alias = "gapFilled")
    private Product gapFilledProduct;

    @TargetProduct
    private Product targetProduct;

    private int rightEdge;
    private int lowerEdge;

    private Product smoothedProduct;

    private RectangleExtender rectCalculator;

    private int pixelsPerCell;

    @Override
    public void initialize() throws OperatorException {

        Map<String, Product> smoothInput = new HashMap<String, Product>(4);
        smoothInput.put("source", gapFilledProduct);
        smoothedProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMSmoothOp.class), GPF.NO_PARAMS, smoothInput);

        if (sourceProduct.getProductType().contains("_RR")) {
            pixelsPerCell = ScapeMConstants.RR_PIXELS_PER_CELL;
        } else {
            pixelsPerCell = ScapeMConstants.FR_PIXELS_PER_CELL;
        }

        rectCalculator = new RectangleExtender(
                new Rectangle(sourceProduct.getSceneRasterWidth(),
                              sourceProduct.getSceneRasterHeight()),
                2 * pixelsPerCell,
                2 * pixelsPerCell);
        createTargetProduct();
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

//        targetProduct.setPreferredTileSize(2*pixelsPerCell, 2*pixelsPerCell);
        targetProduct.setPreferredTileSize(pixelsPerCell, pixelsPerCell);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        Band visibilityBand = targetProduct.addBand(ScapeMConstants.VISIBILITY_BAND_NAME, ProductData.TYPE_FLOAT32);
        visibilityBand.setNoDataValue(ScapeMConstants.VISIBILITY_NODATA_VALUE);
        visibilityBand.setValidPixelExpression(ScapeMConstants.SCAPEM_VALID_EXPR);

        rightEdge = (sourceProduct.getSceneRasterWidth() / pixelsPerCell) * pixelsPerCell;
        lowerEdge = (sourceProduct.getSceneRasterHeight() / pixelsPerCell) * pixelsPerCell;
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        Rectangle targetRect = targetTile.getRectangle();
        Rectangle sourceRect = rectCalculator.extend(targetRect);

        Band gapFilledVisibilityBand = gapFilledProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        Tile gapFilledVisibilityTile = getSourceTile(gapFilledVisibilityBand, sourceRect);

        Band smoothedVisibilityBand = smoothedProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        Tile smoothedVisibilityTile = getSourceTile(smoothedVisibilityBand, sourceRect);

        pm.beginTask("Processing frame...", targetRect.height + 1);
        try {
            // we may have to re-fill pixels from right and lower edge of the scene which were left empty
            // by initial smoothing, which works on complete 30x30km cells only

            boolean isRightEdge = (targetRect.x >= rightEdge - pixelsPerCell);
            boolean isLowerEdge = (targetRect.y >= lowerEdge - pixelsPerCell);

            double[] rightEdgeVisibility = null;
            if (isRightEdge) {
                rightEdgeVisibility = new double[targetRect.height];
                for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                    rightEdgeVisibility[y - targetRect.y] = getRightEdgeVisibilitySample(smoothedVisibilityTile, y);
                    for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                        if (smoothedVisibilityTile.getSampleDouble(x, y) != ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                            targetTile.setSample(x, y, smoothedVisibilityTile.getSampleDouble(x, y));
                        } else {
                            targetTile.setSample(x, y, rightEdgeVisibility[y - targetRect.y]);
                        }
                    }
                }
            }
            if (isLowerEdge) {
                double[] lowerEdgeVisibility = new double[targetRect.width];
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    lowerEdgeVisibility[x - targetRect.x] = getLowerEdgeVisibilitySample(smoothedVisibilityTile, x);
                }
                for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                    for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                        if (smoothedVisibilityTile.getSampleDouble(x, y) != ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                            targetTile.setSample(x, y, smoothedVisibilityTile.getSampleDouble(x, y));
                        } else {
                            double vis;
                            if (isRightEdge &&
                                    rightEdgeVisibility[y - targetRect.y] != ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                                vis = 0.5 * (rightEdgeVisibility[y - targetRect.y] +
                                        lowerEdgeVisibility[x - targetRect.x]);
                            } else {
                                vis = lowerEdgeVisibility[x - targetRect.x];
                            }
                            targetTile.setSample(x, y, vis);
                        }
                    }
                }
            }
            if (!isRightEdge && !isLowerEdge) {
                // no edge - copy existing smoothed values
                for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                    for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                        targetTile.setSample(x, y, smoothedVisibilityTile.getSampleDouble(x, y));
                    }
                }
            }

            // if there are still zeros, take unsmoothed values
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    if (targetTile.getSampleDouble(x, y) == ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                        targetTile.setSample(x, y, gapFilledVisibilityTile.getSampleDouble(x, y));
                    }
                }
            }

            pm.worked(1);
        } catch (Exception e) {
            // todo
            e.printStackTrace();
        }
    }

    private double getRightEdgeVisibilitySample(Tile visibilityTile, int y) {
        int xIndex = rightEdge - 1;
        double rightEdgeVis = visibilityTile.getSampleDouble(xIndex, y);
        while (rightEdgeVis == ScapeMConstants.VISIBILITY_NODATA_VALUE &&
                xIndex >= visibilityTile.getRectangle().x) {
            rightEdgeVis = visibilityTile.getSampleDouble(xIndex--, y);
        }
        return rightEdgeVis;
    }

    private double getLowerEdgeVisibilitySample(Tile visibilityTile, int x) {
        int yIndex = lowerEdge - 1;
        double lowerEdgeVis = visibilityTile.getSampleDouble(x, yIndex);
        while (lowerEdgeVis == ScapeMConstants.VISIBILITY_NODATA_VALUE &&
                yIndex >= visibilityTile.getRectangle().y) {
            lowerEdgeVis = visibilityTile.getSampleDouble(x, yIndex--);
        }
        return lowerEdgeVis;
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMSmoothFillOp.class);
        }
    }
}
