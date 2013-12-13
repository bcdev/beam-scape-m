package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.ProductUtils;

import java.awt.*;

/**
 * Operator for visibility to AOT conversion.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM.visibility.aot", version = "1.0-SNAPSHOT",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2013 Brockmann Consult",
        internal = true,
        description = "Operator for visibility to AOT conversion.")
public class ScapeMVis2AotOp extends ScapeMMerisBasisOp implements Constants {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @SourceProduct(alias = "visibility")
    private Product visibilityProduct;

    @Parameter(description = "If set, use GETASSE30 DEM, otherwise get altitudes from product TPGs",
               label = "Use GETASSE30 DEM",
               defaultValue = "false")
    private boolean useDEM;

    @Parameter(description = "ScapeM AOT Lookup table")
    private ScapeMLut scapeMLut;

    @TargetProduct
    private Product targetProduct;

    private String demName = ScapeMConstants.DEFAULT_DEM_NAME;

    private ElevationModel elevationModel;

    @Override
    public void initialize() throws OperatorException {

        if (useDEM) {
            final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(demName);
            if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
                throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
            }
            elevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);
        }


        createTargetProduct();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = targetTile.getRectangle();
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();

        Tile altitudeTile = geAltitudeTile(targetRect);

        Band visibilityBand = visibilityProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        Tile visibilityTile = getSourceTile(visibilityBand, targetRect);


        double[][] hsurfArrayCell;
        try {
            if (useDEM && altitudeTile == null) {
                hsurfArrayCell = ScapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, elevationModel, scapeMLut);
            } else {
                hsurfArrayCell = ScapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, altitudeTile, scapeMLut);
            }

            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    final double visibility = visibilityTile.getSampleDouble(x, y);
                    if (visibility != ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                        final double aot550 = ScapeMAlgorithm.getCellAot550(visibility,
                                hsurfArrayCell[x-targetRect.x][y-targetRect.y],
                                scapeMLut);
                        targetTile.setSample(x, y, aot550);
                    } else {
                        targetTile.setSample(x, y, ScapeMConstants.AOT_NODATA_VALUE);
                    }
                }
            }
        } catch (Exception e) {
            // todo
            e.printStackTrace();
        }
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        Band aot550Band = targetProduct.addBand(ScapeMConstants.AOT550_BAND_NAME, ProductData.TYPE_FLOAT32);
        aot550Band.setNoDataValue(ScapeMConstants.AOT_NODATA_VALUE);
        aot550Band.setValidPixelExpression(ScapeMConstants.SCAPEM_VALID_EXPR);

        if (sourceProduct.getProductType().contains("_RR")) {
            targetProduct.setPreferredTileSize(ScapeMConstants.RR_PIXELS_PER_CELL, ScapeMConstants.RR_PIXELS_PER_CELL);
        } else {
            targetProduct.setPreferredTileSize(ScapeMConstants.FR_PIXELS_PER_CELL, ScapeMConstants.FR_PIXELS_PER_CELL);
        }
    }

    // todo: duplicated code, move to utils
    private Tile geAltitudeTile(Rectangle targetRect) {
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


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMVis2AotOp.class);
        }
    }
}
