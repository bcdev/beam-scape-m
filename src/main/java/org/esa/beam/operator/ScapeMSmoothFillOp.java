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
 * Operator for finning right and lower edges of smoothed product.
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
            pixelsPerCell = 2*ScapeMConstants.RR_PIXELS_PER_CELL;
        } else {
            pixelsPerCell = 2*ScapeMConstants.FR_PIXELS_PER_CELL;
        }

        rectCalculator = new RectangleExtender(
                new Rectangle(sourceProduct.getSceneRasterWidth(),
                              sourceProduct.getSceneRasterHeight()),
                pixelsPerCell,
                pixelsPerCell);
        createTargetProduct();
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());

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

        Band visibilityBand = smoothedProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        Tile visibilityTile = getSourceTile(visibilityBand, sourceRect);

        try {

            if (targetRect.x == 1080 && targetRect.y == 300) {
                System.out.println("targetRect = " + targetRect);
            }
            boolean isRightEdge = (targetRect.x >= rightEdge);

            if (isRightEdge) {
                for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                    final double rightEdgeVisibility = getRightEdgeVisibilitySample(visibilityTile, y);
                    for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                        if (visibilityTile.getSampleDouble(x, y) != ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                            targetTile.setSample(x, y, visibilityTile.getSampleDouble(x, y));
                        } else {
                            targetTile.setSample(x, y, rightEdgeVisibility);
                        }
                    }
                }
            } else {
                // no edge - copy existing values
                for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                    for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                        targetTile.setSample(x, y, visibilityTile.getSampleDouble(x, y));
                    }
                }
            }
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

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMSmoothFillOp.class);
        }
    }
}
