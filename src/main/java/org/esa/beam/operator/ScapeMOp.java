package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.beam.ScapeMMode;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.algorithms.scapem.FubScapeMOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM", version = "1.0-SNAPSHOT",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2013 Brockmann Consult",
        description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm.")
public class ScapeMOp extends MerisBasisOp implements Constants {
    public static final String VERSION = "1.0-SNAPSHOT";

    @Parameter(description = "AOT processing mode", defaultValue = "SHORT")
    private ScapeMMode aotMode;

    @Parameter(description = "CWV processing mode", defaultValue = "SHORT")
    private ScapeMMode cwvMode;

    @Parameter(description = "DEM name", defaultValue = "GETASSE30")
    private String demName;

    @SourceProduct(description = "MERIS L1B product")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    public static final String RADIANCE_BAND_PREFIX = "radiance";
    public static final String REFL_BAND_PREFIX = "refl";
    public static final String CORR_FLAGS = "scapem_corr_flags";

    public static final int RR_PIXELS_PER_CELL = 30;
    public static final int FR_PIXELS_PER_CELL = 120;

    protected ScapeMCorrection scapeMCorrection;

    private Band[] reflBands;
    private Band flagBand;

    protected L2AuxData l2AuxData;

    private WatermaskClassifier classifier;
    private static int WATERMASK_RESOLUTION_DEFAULT = 50;

    private ElevationModel elevationModel;

    private Product cloudProduct;


    @Override
    public void initialize() throws OperatorException {
        try {
            l2AuxData = L2AuxDataProvider.getInstance().getAuxdata(sourceProduct);
            scapeMCorrection = new ScapeMCorrection(l2AuxData);
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }
//        JAI.getDefaultInstance().getTileScheduler().setParallelism(1);

        try {
            classifier = new WatermaskClassifier(WATERMASK_RESOLUTION_DEFAULT);
        } catch (IOException e) {
            getLogger().warning("Watermask classifier could not be initialized - fallback mode is used.");
        }

        final ElevationModelDescriptor demDescriptor = ElevationModelRegistry.getInstance().getDescriptor(demName);
        if (demDescriptor == null || !demDescriptor.isDemInstalled()) {
            throw new OperatorException("DEM not installed: " + demName + ". Please install with Module Manager.");
        }
        elevationModel = demDescriptor.createDem(Resampling.NEAREST_NEIGHBOUR);

        createTargetProduct();

        // get the cloud product from Idepix...
        Map<String, Product> idepixInput = new HashMap<String, Product>(4);
        idepixInput.put("source", sourceProduct);
        Map<String, Object> params = new HashMap<String, Object>(1);
        cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FubScapeMOp.class), params, idepixInput);


        // transform IDL procedure 'derive_AtmPar_Refl' to Java...:

        // 1. AOT retrieval on 30x30km cells:
        //      - set 30x30km cell grid. residuals in last column/row are filled to previous cells,
        //          so cell indices should be e.g. like this for y dim in case of SCENE_HEIGHT=305 (same for x dim):
        //          IDL> PRINT, y_end_arr
        //                  29          59          89         119         149
        //                  179         209         239         269         304
        //      - apply 'cloud mask 2' over all pixels in cell
        //      - consider only cells with more than 35% cloud-free land pixels
        //      - compute VISIBILITY for those cells  (--> 'interpol_lut')
        //      - refinement of AOT retrieval:
        //          ** in given cell, consider only land pixels defined from 'cloud mask 1'
        //          ** from these, determine 5 'reference pixels' ( --> 'extract_ref_pixels')
        //             and derive visibility for cell from them ( --> 'inversion_MERIS_AOT')
        //      - fill gaps (<35% land): interpolate from surrounding cells ( --> 'fill_gaps_vis_new')
        //      - spatial smoothing by cubic convolution
        //      - conversion visibility --> AOT550

        // 2. CWV retrieval:
        //      - minimise 'Merit' function with Brent method ( --> 'ZBRENT', 'chisq_merisWV')
        //
        // 3. Reflectance retrieval:
        //      - from LUT (--> 'interpol_lut' called from  'derive_AtmPar_Refl'), but using
        //        different approach for bands 2, 11, 15 (see paper)
        //

    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRect, ProgressMonitor pm) throws OperatorException {
//        super.computeTileStack(targetTiles, targetRect, pm);

        Tile szaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect);
        Tile vzaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect);
        Tile saaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect);
        Tile vaaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect);

        Tile[] radianceTiles = new Tile[L1_BAND_NUM];
        Band[] radianceBands = new Band[L1_BAND_NUM];
        for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
            radianceBands[bandId] = sourceProduct.getBand(RADIANCE_BAND_PREFIX + "_" + (bandId + 1));
            radianceTiles[bandId] = getSourceTile(radianceBands[bandId], targetRect);
        }

        double[] toaMinCell = new double[L1_BAND_NUM];

        final GeoCoding geoCoding = sourceProduct.getGeoCoding();

        final Tile cloudFlagsTile = getSourceTile(cloudProduct.getBand(IdepixUtils.IDEPIX_CLOUD_FLAGS), targetRect,
                BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final boolean cellIsClear35Percent =
                scapeMCorrection.isCellClearLand(targetRect, geoCoding, cloudFlagsTile, classifier, 0.35);

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
                final double[][] hsurfArrayCell = scapeMCorrection.getHsurfArrayCell(targetRect, geoCoding, classifier, elevationModel);
                final double hsurfMeanCell = scapeMCorrection.getHsurfMeanCell(hsurfArrayCell, geoCoding, classifier);

                final double[][] cosSzaArrayCell = scapeMCorrection.getCosSzaArrayCell(targetRect, szaTile);
                final double cosSzaMeanCell = scapeMCorrection.getCosSzaMeanCell(cosSzaArrayCell, geoCoding, classifier);

                final int doy = sourceProduct.getStartTime().getAsCalendar().get(Calendar.DAY_OF_YEAR);
                double[][][] toaArrayCell = new double[L1_BAND_NUM][targetRect.width][targetRect.height];
                for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                    toaArrayCell[bandId] = scapeMCorrection.getToaArrayCell(radianceTiles[bandId], targetRect, geoCoding, doy, classifier);
                    toaMinCell[bandId] = scapeMCorrection.getToaMinCell(toaArrayCell[bandId]);
                }

                // now get first visibility estimate...
                final boolean cellIsClear45Percent =
                        scapeMCorrection.isCellClearLand(targetRect, geoCoding, cloudFlagsTile, classifier, 0.45);
                double firstVisibility =
                        scapeMCorrection.getCellVisibility(toaArrayCell,
                                toaMinCell, vza, sza, phi,
                                hsurfArrayCell,
                                hsurfMeanCell,
                                cosSzaArrayCell,
                                cosSzaMeanCell,
                                cellIsClear45Percent);

                // todo: continue
            } catch (Exception e) {
                // todo
                e.printStackTrace();
            }
        } else {
            // todo
        }
    }


    private void createTargetProduct() throws OperatorException {
        targetProduct = createCompatibleProduct(sourceProduct, "MER", "MER_L2");

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        reflBands = addBandGroup(REFL_BAND_PREFIX);

        flagBand = targetProduct.addBand(CORR_FLAGS, ProductData.TYPE_INT16);
        FlagCoding flagCoding = createFlagCoding(reflBands.length);
        flagBand.setSampleCoding(flagCoding);
        targetProduct.getFlagCodingGroup().add(flagCoding);

        if (sourceProduct.getProductType().contains("_RR")) {
            targetProduct.setPreferredTileSize(RR_PIXELS_PER_CELL, RR_PIXELS_PER_CELL);
        } else {
            targetProduct.setPreferredTileSize(FR_PIXELS_PER_CELL, FR_PIXELS_PER_CELL);
        }
    }

    private Band[] addBandGroup(String prefix) {
        Band[] bands = new Band[L1_BAND_NUM];
        for (int i = 0; i < L1_BAND_NUM; i++) {
            Band targetBand = targetProduct.addBand(prefix + "_" + (i + 1), ProductData.TYPE_FLOAT32);
            final String srcBandName = RADIANCE_BAND_PREFIX + "_" + (i + 1);
            ProductUtils.copySpectralBandProperties(sourceProduct.getBand(srcBandName), targetBand);
            targetBand.setNoDataValueUsed(true);
            targetBand.setNoDataValue(BAD_VALUE);
            bands[i] = targetBand;
        }
        return bands;
    }

    public static FlagCoding createFlagCoding(int bandLength) {
        FlagCoding flagCoding = new FlagCoding(CORR_FLAGS);
        int bitIndex = 0;
        for (int i = 0; i < L1_BAND_NUM; i++) {
            flagCoding.addFlag("F_NEGATIV_BRR_" + (i + 1), BitSetter.setFlag(0, bitIndex), null);
            bitIndex++;
        }
        return flagCoding;
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMOp.class);
        }
    }
}
