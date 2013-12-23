package org.esa.beam.operator;

import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.util.ProductUtils;

import java.awt.*;

/**
 * A copy of MerisBasisOp, but suppressing the 'copyAllTiePoints' option
 *
 * @author olafd
 */
public abstract class ScapeMMerisBasisOp extends Operator {

    /**
     * creates a new product with the same size
     *
     * @param sourceProduct
     * @param name
     * @param type
     * @return targetProduct
     */
    public Product createCompatibleProduct(Product sourceProduct, String name, String type) {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();

        Product targetProduct = new Product(name, type, sceneWidth, sceneHeight);
        copyProductTrunk(sourceProduct, targetProduct);

        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        if (sourceProduct.getProductType().contains("_RR")) {
            targetProduct.setPreferredTileSize(ScapeMConstants.RR_PIXELS_PER_CELL, ScapeMConstants.RR_PIXELS_PER_CELL);
        } else {
            targetProduct.setPreferredTileSize(ScapeMConstants.FR_PIXELS_PER_CELL, ScapeMConstants.FR_PIXELS_PER_CELL);
        }

        return targetProduct;
    }

    /**
     * Copies basic information for a MERIS product to the target product
     *
     * @param sourceProduct
     * @param targetProduct
     */
    public void copyProductTrunk(Product sourceProduct,
                                 Product targetProduct) {
        copyTiePoints(sourceProduct, targetProduct);
        copyBaseGeoInfo(sourceProduct, targetProduct);
    }

    /**
     * Copies the tie point data.
     *
     * @param sourceProduct
     * @param targetProduct
     */
    private void copyTiePoints(Product sourceProduct,
                               Product targetProduct) {
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
    }

    /**
     * Copies geocoding and the start and stop time.
     *
     * @param sourceProduct
     * @param targetProduct
     */
    private void copyBaseGeoInfo(Product sourceProduct,
                                 Product targetProduct) {
        // copy geo-coding to the output product
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());
    }

    Tile getAltitudeTile(Rectangle targetRect, Product sourceProduct, boolean useDEM) {
        Tile demTile = null;
        Band demBand;
        if (useDEM) {
            demBand = sourceProduct.getBand("dem_elevation");
            if (demBand != null) {
                demTile = getSourceTile(demBand, targetRect);
            }
        } else {
            Band frAltitudeBand = sourceProduct.getBand("altitude");
            if (frAltitudeBand != null) {
                // FR, FSG
                demTile = getSourceTile(frAltitudeBand, targetRect);
            } else {
                // RR
                TiePointGrid rrAltitudeTpg = sourceProduct.getTiePointGrid("dem_alt");
                if (rrAltitudeTpg != null) {
                    demTile = getSourceTile(rrAltitudeTpg, targetRect);
                } else {
                    throw new OperatorException
                            ("Cannot attach altitude information from given input and configuration - please check!");
                }
            }
        }
        return demTile;
    }


}
