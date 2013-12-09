package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.dataio.envisat.EnvisatConstants;
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
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.ClearLandAndWaterPixelStrategy;
import org.esa.beam.util.ClearLandPixelStrategy;
import org.esa.beam.util.ClearPixelStrategy;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.util.Calendar;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm: cell visibility retrieval part.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM.visibility", version = "1.0-SNAPSHOT",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2013 Brockmann Consult",
        internal = true,
        description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm: cell visibility retrieval part.")
public class ScapeMVisibilityOp extends MerisBasisOp implements Constants {

    @Parameter(description = "DEM name", defaultValue = "GETASSE30")
    private String demName;

    @Parameter(description = "ScapeM AOT Lookup table")
    private ScapeMLut scapeMLut;

    @Parameter(description = "Compute also over all water", defaultValue = "false")
    private boolean computeOverWater;

    @SourceProduct(alias = "source")
    private Product sourceProduct;
    @SourceProduct(alias = "cloud")
    private Product cloudProduct;

    @TargetProduct
    private Product targetProduct;

    public static final String RADIANCE_BAND_PREFIX = "radiance";

    private ElevationModel elevationModel;
    private ClearPixelStrategy clearPixelStrategy;

    @Override
    public void initialize() throws OperatorException {
        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        elevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);

        if(computeOverWater) {
            clearPixelStrategy = new ClearLandAndWaterPixelStrategy(cloudProduct.getBandAt(0));
        } else {
            clearPixelStrategy = new ClearLandPixelStrategy(cloudProduct.getBandAt(0));
        }

        createTargetProduct();
    }

    @Override
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = targetTile.getRectangle();

        final Tile szaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect);
        final Tile vzaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect);
        final Tile saaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect);
        final Tile vaaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect);

        Tile demTile = null;
        Band demBand = sourceProduct.getBand("dem_elevation");
        if (demBand != null) {
            demTile = getSourceTile(demBand, targetRect);
        }

        Tile[] radianceTiles = new Tile[L1_BAND_NUM];
        Band[] radianceBands = new Band[L1_BAND_NUM];
        for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
            radianceBands[bandId] = sourceProduct.getBand(RADIANCE_BAND_PREFIX + "_" + (bandId + 1));
            radianceTiles[bandId] = getSourceTile(radianceBands[bandId], targetRect);
        }

        double[] toaMinCell = new double[L1_BAND_NUM];

        final GeoCoding geoCoding = sourceProduct.getGeoCoding();

//        final Tile cloudFlagsTile = getSourceTile(cloudProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS), targetRect,
//                BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final boolean cellIsClear35Percent =
                ScapeMAlgorithm.isCellClearLand(targetRect, clearPixelStrategy, 0.35);

        if (targetRect.x == 30 && targetRect.y == 0) {
//            System.out.println("targetRect = " + targetRect);
        }
        if (cellIsClear35Percent) {
            // compute visibility...

            final int centerX = targetRect.x + targetRect.width / 2;
            final int centerY = targetRect.y + targetRect.height / 2;

            final double vza = vzaTile.getSampleDouble(centerX, centerY);
            final double sza = szaTile.getSampleDouble(centerX, centerY);
            final double vaa = vaaTile.getSampleDouble(centerX, centerY);
            final double saa = saaTile.getSampleDouble(centerX, centerY);
            final double phi = HelperFunctions.computeAzimuthDifference(vaa, saa);

            try {
                if (targetRect.x == 30 && targetRect.y == 0) {
//                    System.out.println("targetRect = " + targetRect);
                }
                double[][] hsurfArrayCell;
                if (demTile != null) {
                    hsurfArrayCell = ScapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, demTile, scapeMLut);
                } else {
                    hsurfArrayCell = ScapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, elevationModel, scapeMLut);
                }

                final double hsurfMeanCell = ScapeMAlgorithm.getHsurfMeanCell(hsurfArrayCell, targetRect, clearPixelStrategy);

                final double[][] cosSzaArrayCell = ScapeMAlgorithm.getCosSzaArrayCell(targetRect, szaTile);
                final double cosSzaMeanCell = ScapeMAlgorithm.getCosSzaMeanCell(cosSzaArrayCell, targetRect, clearPixelStrategy);

                final int doy = sourceProduct.getStartTime().getAsCalendar().get(Calendar.DAY_OF_YEAR);
                double[][][] toaArrayCell = new double[L1_BAND_NUM][targetRect.width][targetRect.height];
                for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                    toaArrayCell[bandId] = ScapeMAlgorithm.getToaArrayCell(radianceTiles[bandId], targetRect, doy);
                    toaMinCell[bandId] = ScapeMAlgorithm.getToaMinCell(toaArrayCell[bandId]);
                }

                // now get visibility estimate...
                final boolean cellIsClear45Percent =
                        ScapeMAlgorithm.isCellClearLand(targetRect, clearPixelStrategy, 0.45);
                final double visibility = ScapeMAlgorithm.getCellVisibility(toaArrayCell,
                        toaMinCell, vza, sza, phi,
                        hsurfArrayCell,
                        hsurfMeanCell,
                        cosSzaArrayCell,
                        cosSzaMeanCell,
                        cellIsClear45Percent,
                        scapeMLut);

                setCellVisibilitySamples(targetTile, targetRect, visibility);
            } catch (Exception e) {
                // todo
                e.printStackTrace();
                setCellVisibilitySamples(targetTile, targetRect, ScapeMConstants.AOT_NODATA_VALUE);
            }
        } else {
            setCellVisibilitySamples(targetTile, targetRect, ScapeMConstants.AOT_NODATA_VALUE);
        }
    }

    private void setCellVisibilitySamples(Tile targetTile, Rectangle targetRect, double visibility) {
        for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
            for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                targetTile.setSample(x, y, visibility);
            }
        }
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        if (sourceProduct.getBand("dem_elevation") != null) {
            ProductUtils.copyBand("dem_elevation", sourceProduct, targetProduct, true);
        }

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        Band visibilityBand = targetProduct.addBand(ScapeMConstants.VISIBILITY_BAND_NAME, ProductData.TYPE_FLOAT32);
        visibilityBand.setNoDataValue(ScapeMConstants.VISIBILITY_NODATA_VALUE);
        visibilityBand.setValidPixelExpression(ScapeMConstants.SCAPEM_VALID_EXPR);

        if (sourceProduct.getProductType().contains("_RR")) {
            targetProduct.setPreferredTileSize(ScapeMConstants.RR_PIXELS_PER_CELL, ScapeMConstants.RR_PIXELS_PER_CELL);
        } else {
            targetProduct.setPreferredTileSize(ScapeMConstants.FR_PIXELS_PER_CELL, ScapeMConstants.FR_PIXELS_PER_CELL);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMVisibilityOp.class);
        }
    }
}
