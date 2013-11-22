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
import org.esa.beam.idepix.algorithms.scapem.FubScapeMClassificationOp;
import org.esa.beam.idepix.algorithms.scapem.FubScapeMOp;
import org.esa.beam.io.LutAccess;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.ScapeMUtils;
import org.esa.beam.util.math.LookupTable;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import javax.media.jai.BorderExtender;
import javax.media.jai.JAI;
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

    private Band isLandBand;
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
    public void computeTile(Band targetBand, Tile targetTile, ProgressMonitor pm) throws OperatorException {

        final Rectangle targetRect = targetTile.getRectangle();
        final GeoCoding geoCoding = sourceProduct.getGeoCoding();

        final Tile szaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_ZENITH_DS_NAME), targetRect,
                                           BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile vzaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_ZENITH_DS_NAME), targetRect,
                                           BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile saaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_SUN_AZIMUTH_DS_NAME), targetRect,
                                           BorderExtender.createInstance(BorderExtender.BORDER_COPY));
        final Tile vaaTile = getSourceTile(sourceProduct.getTiePointGrid(EnvisatConstants.MERIS_VIEW_AZIMUTH_DS_NAME), targetRect,
                                           BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final int spectralBandIndex = targetBand.getSpectralBandIndex();
        final String srcBandName = RADIANCE_BAND_PREFIX + "_" + (spectralBandIndex + 1);
        final Tile radianceTile = getSourceTile(sourceProduct.getBand(srcBandName), targetRect);

        // todo: activate when cloud mask is ready
        final Tile cloudFlagsTile = null;
//        final Tile cloudFlagsTile = getSourceTile(cloudProduct.getBand(FubScapeMClassificationOp.CLOUD_FLAGS), targetRect,
//                                        BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        final boolean cellIsClear = scapeMCorrection.isCellClearLand(targetRect, geoCoding, cloudFlagsTile, classifier);

        if (cellIsClear) {
            // compute visibility...

            final int centerX = targetRect.x + targetRect.width / 2;
            final int centerY = targetRect.y + targetRect.height / 2;

            final double vza = vzaTile.getSampleDouble(centerX, centerY);
            final double sza = szaTile.getSampleDouble(centerX, centerY);
            final double vaa = vaaTile.getSampleDouble(centerX, centerY);
            final double saa = saaTile.getSampleDouble(centerX, centerY);
            final double phi = HelperFunctions.computeAzimuthDifference(vaa, saa);

            try {
                final double hsurf = scapeMCorrection.getHsurfMeanCell(targetRect, geoCoding, classifier, elevationModel);
//                if no illumination, mus_il = mat_cos = cos(sza_img * !dtor):
                final double musIl = scapeMCorrection.getCosSzaMeanCell(targetRect, geoCoding, classifier, szaTile);

                final int doy = sourceProduct.getStartTime().getAsCalendar().get(Calendar.DAY_OF_YEAR);
                if (targetRect.x == 30 && targetRect.y == 0) {
                    System.out.println("targetRect = " + targetRect);
                }
                double toaMinCell =
                        scapeMCorrection.getToaMinCell(radianceTile, targetRect, geoCoding, doy, spectralBandIndex, classifier);
                System.out.println();

                // now get first visibility estimate...
                double firstVisibility = scapeMCorrection.getFirstVisibility(toaMinCell, vza, sza, phi, hsurf);


            } catch (Exception e) {
                // todo
                e.printStackTrace();
            }


//            dem_sub = dem_img[x_ini:x_end, y_ini:y_end]
//            mus_il_sub = mus_il_img[x_ini:x_end, y_ini:y_end]    ; if no illumination, mus_il = mat_cos = cos(sza_img * !dtor)
//            rad_sub = rad_img[x_ini:x_end, y_ini:y_end, *]
//
//            mus_il = mean(mus_il_sub[wh_land_all])
//            hsurf = mean(dem_sub[wh_land_all])
//
//            rad_sub_arr = fltarr(cont_land_all, num_bd)
//            for jj = 0, num_bd - 1 do rad_sub_arr[*, jj] = rad_sub[wh_land_all + jj * tot_pix] * fac * cal_coef[jj]
//
//            min_toa = min(rad_sub_arr, dimension=1)           OK UP TO THIS POINT  !!!
//
//            n_vis = where(wl_center gt 680.)
//            n_vis = n_vis[0]
//            stp = [1., 0.1]
//            vis = vis_gr[0] - stp[0]
//            for i = 0, 1 do begin
//            if i eq 1 then vis = (vis - stp[0]) > vis_gr[0]
//            repeat begin
//            vis = vis + stp[i]
//            f_int = interpol_lut(vza, sza, phi, hsurf, vis, wv)  ; OD: I understand that this result represents a 30x30km cell...
//            wh_neg = where(min_toa[0:n_vis] le reform(f_int[0, 0:n_vis]), cnt_neg)
//            endrep until (cnt_neg eq 0 or vis+stp[i] ge vis_gr[dim_vis - 1])
//            endfor
//                    vis_lim = vis - stp[1]
//            vis_val = vis_lim
//            valid_flg =2
//            ;stop
//            ;       if vis_val le vis_ref then begin
//            ;         vis_val = 0.
//            ;         valid_flg = 0
//            ;       endif
//
//            if float(cont_land_bri) / tot_pix gt 0.45 then begin
//                    ; OD: get reference pixels and compute 'reference' visibility...
//            mus_il_sub = mus_il_sub[wh_land_bri]
//            dem_sub = dem_sub[wh_land_bri]
//
//            mus_il = mean(mus_il_sub)
//            hsurf = mean(dem_sub)
//
//            rad_sub_arr = fltarr(cont_land_bri, num_bd)
//            for jj = 0, num_bd - 1 do rad_sub_arr[*, jj] = rad_sub[wh_land_bri + jj * tot_pix] * fac * cal_coef[jj]
//
//            extract_ref_pixels, dem_sub, mus_il_sub, rad_sub_arr, width_win, height_win, num_bd, num_pix, wl_center, ref_pix_all, valid_flg
//
//            if valid_flg eq 1 then begin
//                    ;min_toa = fltarr(num_bd)
//            ;for k = 0, num_bd - 1 do min_toa[k] = min(rad_sub_arr[*, k])
//            min_toa = min(rad_sub_arr, dimension=1)
//
//            inversion_MERIS_AOT, num_pix, num_bd, ref_pix_all, wl_center, vza, sza, phi, hsurf, wv, mus_il, vis_lim, AOT_time_flg, $
//            vis_val, vis_stddev, EM_code
//            EM_code_mat[indx,indy] = EM_code
//            mat_codes[indx, indy] = valid_flg
//
//                    endif


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
