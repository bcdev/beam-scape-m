package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.dataio.envisat.EnvisatConstants;
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
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.io.LutAccess;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ScapeMUtils;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.util.Calendar;
import java.util.Map;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm: AC part.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM.ac", version = "1.0-SNAPSHOT",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2013 Brockmann Consult",
        internal = true,
        description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm: AC part.")
public class ScapeMAtmosCorrOp extends MerisBasisOp implements Constants {

    @Parameter(description = "DEM name", defaultValue = "GETASSE30")
    private String demName;

    @Parameter(description = "ScapeM AOT Lookup table")
    private ScapeMLut scapeMLut;

    @Parameter(description = "Number of iterations for WV retrieval", defaultValue = "1")
    private int numWvIterations;

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @SourceProduct(alias = "cloud")
    private Product cloudProduct;

    @SourceProduct(alias = "visibility")
    private Product visibilityProduct;

    @TargetProduct
    private Product targetProduct;

    private ElevationModel elevationModel;

    private Band[] reflBands;
    private Band flagBand;

    public static final String RADIANCE_BAND_PREFIX = "radiance";
    public static final String REFL_BAND_PREFIX = "refl";
    public static final String CORR_FLAGS = "scapem_corr_flags";

    @Override
    public void initialize() throws OperatorException {
        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        elevationModel = demDescriptor.createDem(Resampling.BILINEAR_INTERPOLATION);

        createTargetProduct();
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws OperatorException {
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();

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
        double[] solirr = new double[L1_BAND_NUM];
        for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
            radianceBands[bandId] = sourceProduct.getBand(RADIANCE_BAND_PREFIX + "_" + (bandId + 1));
            radianceTiles[bandId] = getSourceTile(radianceBands[bandId], targetRect);
            solirr[bandId] = radianceBands[bandId].getSolarFlux() / 1.E-4;
        }

        Band visibilityBand = visibilityProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        Tile visibilityTile = getSourceTile(visibilityBand, targetRect);

        final Tile cloudFlagsTile = getSourceTile(cloudProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS), targetRect,
                BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final int centerX = targetRect.x + targetRect.width / 2;
        final int centerY = targetRect.y + targetRect.height / 2;

        final double vza = vzaTile.getSampleDouble(centerX, centerY);
        final double sza = szaTile.getSampleDouble(centerX, centerY);
        final double vaa = vaaTile.getSampleDouble(centerX, centerY);
        final double saa = saaTile.getSampleDouble(centerX, centerY);
        final double phi = HelperFunctions.computeAzimuthDifference(vaa, saa);

        double[][] hsurfArrayCell;
        try {
            if (demTile != null) {
                hsurfArrayCell = ScapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, demTile, scapeMLut);
            } else {
                hsurfArrayCell = ScapeMAlgorithm.getHsurfArrayCell(targetRect, geoCoding, elevationModel, scapeMLut);
            }
            final double hsurfMeanCell = ScapeMAlgorithm.getHsurfMeanCell(hsurfArrayCell, cloudFlagsTile);
            final double[][] cosSzaArrayCell = ScapeMAlgorithm.getCosSzaArrayCell(targetRect, szaTile);


            final int doy = sourceProduct.getStartTime().getAsCalendar().get(Calendar.DAY_OF_YEAR);
            double[][][] toaArrayCell = new double[L1_BAND_NUM][targetRect.width][targetRect.height];
            for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                toaArrayCell[bandId] = ScapeMAlgorithm.getToaArrayCell(radianceTiles[bandId], targetRect, doy);
            }

            Tile[] reflTiles = getTargetTileGroup(reflBands, targetTiles);

            final int dimWv = scapeMLut.getCwvArrayLUT().length;
            final int dimVis = scapeMLut.getVisArrayLUT().length;
            final int dimHurf = scapeMLut.getHsfArrayLUT().length;
            double[][][][] lpw = new double[L1_BAND_NUM][dimWv][dimVis][dimHurf];     // [15][6][7][3]
            double[][][][] e0tw = new double[L1_BAND_NUM][dimWv][dimVis][dimHurf];
            double[][][][] ediftw = new double[L1_BAND_NUM][dimWv][dimVis][dimHurf];
            double[][][][] sab = new double[L1_BAND_NUM][dimWv][dimVis][dimHurf];
            double[][][][] tDirD = new double[L1_BAND_NUM][dimWv][dimVis][dimHurf];

            for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                for (int i = 0; i < dimWv; i++) {
                    for (int j = 0; j < dimVis; j++) {
                        for (int k = 0; k < dimHurf; k++) {
                            double[][] fInt = LutAccess.interpolAtmParamLut(scapeMLut.getAtmParamLut(),
                                    vza, sza, phi,
                                    scapeMLut.getHsfArrayLUT()[k],
                                    scapeMLut.getVisArrayLUT()[j],
                                    scapeMLut.getCwvArrayLUT()[i]);
                            lpw[bandId][i][j][k] = fInt[bandId][0];
                            e0tw[bandId][i][j][k] = fInt[bandId][1];
                            ediftw[bandId][i][j][k] = fInt[bandId][2];
                            sab[bandId][i][j][k] = fInt[bandId][4];
                            tDirD[bandId][i][j][k] =
                                    fInt[bandId][1] / (fInt[bandId][5] * (1.0 + fInt[bandId][3] * solirr[bandId]));
                        }
                    }
                }
            }

            ScapeMResult acResult = new ScapeMResult();
            double wvInit = ScapeMConstants.WV_INIT;
            for (int i = 0; i < numWvIterations; i++) {
                double[][] fInt = LutAccess.interpolAtmParamLut(scapeMLut.getAtmParamLut(),
                        vza, sza, phi, hsurfMeanCell, ScapeMConstants.VIS_INIT, wvInit);
                double[][][] reflImage = ScapeMAlgorithm.getReflImage(fInt, toaArrayCell, cosSzaArrayCell);

                acResult = ScapeMAlgorithm.computeAcResult(targetRect,
                        visibilityTile,
                        hsurfArrayCell,
                        cosSzaArrayCell,
                        reflImage,
                        radianceTiles[13],
                        radianceTiles[14],
                        scapeMLut,
                        lpw, e0tw, ediftw, tDirD, sab);

                wvInit = ScapeMUtils.getMeanDouble2D(acResult.getWv());
            }

            for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                Tile reflTile = reflTiles[bandId];
                for (int y = targetRect.y; y < targetRect.y + targetRect.height; y++) {
                    for (int x = targetRect.x; x < targetRect.x + targetRect.width; x++) {
                        reflTile.setSample(x, y, acResult.getReflPixel(bandId, x, y));
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

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        reflBands = addBandGroup(REFL_BAND_PREFIX);

        flagBand = targetProduct.addBand(CORR_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding();
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        if (sourceProduct.getProductType().contains("_RR")) {
            targetProduct.setPreferredTileSize(ScapeMConstants.RR_PIXELS_PER_CELL,
                    ScapeMConstants.RR_PIXELS_PER_CELL);
        } else {
            targetProduct.setPreferredTileSize(ScapeMConstants.FR_PIXELS_PER_CELL,
                    ScapeMConstants.FR_PIXELS_PER_CELL);
        }
    }

    private Band[] addBandGroup(String prefix) {
        Band[] bands = new Band[L1_BAND_NUM];
        for (int i = 0; i < L1_BAND_NUM; i++) {
            Band targetBand = targetProduct.addBand(prefix + "_" + (i + 1), ProductData.TYPE_FLOAT32);
            final String srcBandName = RADIANCE_BAND_PREFIX + "_" + (i + 1);
            ProductUtils.copySpectralBandProperties(sourceProduct.getBand(srcBandName), targetBand);
            targetBand.setNoDataValueUsed(true);
            targetBand.setNoDataValue(BAD_VALUE);   // todo: check this value
            bands[i] = targetBand;
        }
        return bands;
    }

    private Tile[] getTargetTileGroup(Band[] bands, Map<Band, Tile> targetTiles) {
        final Tile[] bandRaster = new Tile[L1_BAND_NUM];
        for (int i = 0; i < bands.length; i++) {
            Band band = bands[i];
            if (band != null) {
                bandRaster[i] = targetTiles.get(band);
            }
        }
        return bandRaster;
    }

    public static FlagCoding createFlagCoding() {
        // todo: check if needed
        FlagCoding flagCoding = new FlagCoding(CORR_FLAGS);
        int bitIndex = 0;
        for (int i = 0; i < L1_BAND_NUM; i++) {
            flagCoding.addFlag("F_BAD_VALUE_" + (i + 1), BitSetter.setFlag(0, bitIndex), null);
            bitIndex++;
            // todo: continue if needed
        }
        return flagCoding;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMAtmosCorrOp.class);
        }
    }

}
