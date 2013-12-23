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

    @Parameter(description = "If set, use GETASSE30 DEM, otherwise get altitudes from product TPGs",
               label = "Use GETASSE30 DEM",
               defaultValue = "false")
    private boolean useDEM;

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @SourceProduct(alias = "visibility")
    private Product visibilityProduct;

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

        Tile altitudeTile = getAltitudeTile(targetRect, sourceProduct, useDEM);

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

        Band aot550Band = targetProduct.addBand(ScapeMConstants.AOT550_BAND_NAME, ProductData.TYPE_FLOAT32);
        aot550Band.setNoDataValue(ScapeMConstants.AOT_NODATA_VALUE);
        aot550Band.setValidPixelExpression(ScapeMConstants.SCAPEM_VALID_EXPR);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMVis2AotOp.class);
        }
    }
}
