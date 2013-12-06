package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.ProductUtils;

import java.awt.*;

/**
 * Created with IntelliJ IDEA.
 * User: olafd
 * Date: 05.12.13
 * Time: 23:38
 * To change this template use File | Settings | File Templates.
 */
public class ScapeMVis2AotOp extends MerisBasisOp implements Constants {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @SourceProduct(alias = "visibility")
    private Product visibiliyProduct;

    @Parameter(description = "DEM name", defaultValue = "GETASSE30")
    private String demName;

    @TargetProduct
    private Product targetProduct;

    protected ScapeMAlgorithm scapeMAlgorithm;
    private ElevationModel elevationModel;

    @Override
    public void initialize() throws OperatorException {
        scapeMAlgorithm = new ScapeMAlgorithm(sourceProduct);

        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        elevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);

        createTargetProduct();

    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = targetTile.getRectangle();
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();

        Tile demTile = null;
        Band demBand = sourceProduct.getBand("dem_elevation");   // todo: make sure this has been copied to the visibility product!
        if (demBand != null) {
            demTile = getSourceTile(demBand, targetRect);
        }

        Band visibilityBand = visibiliyProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        Tile visibilityTile = getSourceTile(visibilityBand, targetRect);


        double[][] hsurfArrayCell;
        try {
            if (demTile != null) {
                hsurfArrayCell = scapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, demTile);
            } else {
                hsurfArrayCell = scapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, elevationModel);
            }
            for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                    final double visibility = visibilityTile.getSampleDouble(x, y);
                    if (visibility != ScapeMConstants.VISIBILITY_NODATA_VALUE) {
                        final double aot550 =
                                scapeMAlgorithm.getCellAot550(visibility, hsurfArrayCell[x-targetRect.x][y-targetRect.y]);
                        targetTile.setSample(x, y, aot550);
                    } else {
                        targetTile.setSample(x, y, ScapeMConstants.AOT_NODATA_VALUE);
                    }
                }
            }
        } catch (Exception e) {
            // todo
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMVis2AotOp.class);
        }
    }
}
