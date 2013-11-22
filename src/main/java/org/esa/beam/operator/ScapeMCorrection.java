package org.esa.beam.operator;


import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.io.LutAccess;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.util.ScapeMUtils;
import org.esa.beam.util.math.LookupTable;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;

/**
 * Class representing SCAPE-M algorithm
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMCorrection implements Constants {

    // Auxdata
    protected L2AuxData l2AuxData;
    protected LookupTable atmParamLut;

    private double vzaMin;
    private double vzaMax;
    private double szaMin;
    private double szaMax;
    private double raaMin;
    private double raaMax;
    private double hsfMin;
    private double hsfMax;
    private double visMin;
    private double visMax;
    private double cwvMin;
    private double cwvMax;

    private double[] visArray;


    private static double wvInit = 2.0;

    public ScapeMCorrection(L2AuxData auxData) {
        l2AuxData = auxData;
        readAuxdata();
    }

    /**
     * Determines if cell is regarded as 'clear land' : > 35% must not be water or cloud
     *
     * @param rect
     * @param geoCoding
     * @param cloudFlags
     * @param classifier
     * @return
     */
    boolean isCellClearLand(Rectangle rect, GeoCoding geoCoding, Tile cloudFlags, WatermaskClassifier classifier) {
        int countWater = 0;
        int countCloud2 = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                // todo: activate when cloud mask is ready
//                if (cloudFlags.getSampleBit(x, y, FubScapeMClassificationOp.F_CLOUD_2)) {
//                    countCloud2++;
//                }

                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    // todo: activate when cloud mask is ready
//                    try {
//                        if (classifier.isWater(geoPos.lat, geoPos.lon) &&
//                                !cloudFlags.getSampleBit(x, y, FubScapeMClassificationOp.F_LAKE)) {
//                            countWater++;
//                        }
//                    } catch (IOException ignore) {
//                    }
                }

            }
        }
        return (countCloud2 + countWater) / (rect.getWidth() * rect.getHeight()) <= 0.65;
    }

    /**
     * Returns the elevation mean value (in km) over all land pixels in a 30x30km cell
     *
     * @return
     */
    double getHsurfMeanCell(Rectangle rect,
                            GeoCoding geoCoding,
                            WatermaskClassifier classifier,
                            ElevationModel elevationModel) throws Exception {

        double elevMean = 0.0;
        int elevCount = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    try {
                        if (!classifier.isWater(geoPos.lat, geoPos.lon)) {
                            elevMean += elevationModel.getElevation(geoPos);
                            elevCount++;
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
        }

        double hSurf = 0.001 * elevMean / elevCount;    // km
        return hSurf;
    }

    double getCosSzaMeanCell(Rectangle rect,
                             GeoCoding geoCoding,
                             WatermaskClassifier classifier,
                             Tile szaTile) throws Exception {

        double cosSzaMean = 0.0;
        int cosSzaCount = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    try {
                        if (!classifier.isWater(geoPos.lat, geoPos.lon)) {
                            final double sza = szaTile.getSampleDouble(x, y);
                            cosSzaMean += Math.cos(sza * MathUtils.DTOR);
                            cosSzaCount++;
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
        }

        return cosSzaMean / cosSzaCount;
    }

    double getToaMinCell(Tile radianceTile,
                         Rectangle rect,
                         GeoCoding geoCoding,
                         int doy,
                         int spectralBandIndex,
                         WatermaskClassifier classifier) throws Exception {

        double varSol = ScapeMUtils.varSol(doy);
        final double solFactor = varSol * varSol * 1.E-4;

        double toaMin = Double.MAX_VALUE;
        int index = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    try {
                        if (!classifier.isWater(geoPos.lat, geoPos.lon)) {
//                            double toa = radianceTile.getSampleDouble(x, y) * solFactor * ScapeMConstants.MERIS_CAL_COEFFS[spectralBandIndex];
                            double toa = radianceTile.getSampleDouble(x, y) * solFactor;
//                            System.out.println("index, toa = " + index + "," + toa);
                            if (toa < toaMin) {
                                toaMin = toa;
                            }
                        }
                    } catch (IOException ignore) {
                    }
                }
                index++;
            }
        }

        return toaMin;
    }


    public double getFirstVisibility(double toaMinCell, double vza, double sza, double raa, double hsf) {

        final int nVis = 7;
        final double[] step = {1.0, 0.1};

        double vis = visArray[0] - step[0];
        for (int i=0; i<=1; i++) {
            if (i == 1) {
                vis = Math.max(vis - step[0], visArray[0]);
            }
            while (vis + step[i] < visArray[i]) {
                vis += step[i];
                double[][] fInt = LutAccess.interpolAtmParamLut(atmParamLut, vza, sza, raa, hsf, vis, wvInit);
                // todo: we need to check over ALL bands 1-6!! :-(
//                wh_neg = where(min_toa[0:n_vis] le reform(f_int[0, 0:n_vis]), cnt_neg)
            }
        }

//        n_vis = where(wl_center gt 680.) ; OD: n_vis = [7,8,9,10,11,12,13,14]
//        n_vis = n_vis[0]                 ; OD: now n_vis = 7...
//        stp = [1., 0.1]                  ; OD: vis_gr = [10.0, 15.0, 23.0, 35.0, 60.0, 100.0, 180.0]
//        vis = vis_gr[0] - stp[0]         ; OD: vis = 9.0
//        for i = 0, 1 do begin
//        if i eq 1 then vis = (vis - stp[0]) > vis_gr[0]
//        repeat begin
//        vis = vis + stp[i]   ; OD: vis = 10.0 if i=0
//        f_int = interpol_lut(vza, sza, phi, hsurf, vis, wv)  ; OD: I understand that this result represents a 30x30km cell...
//        wh_neg = where(min_toa[0:n_vis] le reform(f_int[0, 0:n_vis]), cnt_neg)
//        endrep until (cnt_neg eq 0 or vis+stp[i] ge vis_gr[dim_vis - 1])
//        ; OD: jumps out for vis=56.0,
//        ; f_int=0.00805347    0.0650029    0.0297363     0.584800     0.240637     0.494965     0.237203
//        endfor
//                vis_lim = vis - stp[1]  ; OD: vis = 56.0
//        vis_val = vis_lim       ; OD: vis_val=55.9, this is used in the inversion_MERIS_AOT below
//        valid_flg =2

        return 0;  //To change body of created methods use File | Settings | File Templates.
    }

    private void readAuxdata() {
        try {
            atmParamLut = LutAccess.getAtmParmsLookupTable();
        } catch (IOException e) {
            throw new OperatorException(e.getMessage());
        }

        final double[] vzaArray = atmParamLut.getDimension(0).getSequence();
        vzaMin = vzaArray[0];
        vzaMax = vzaArray[vzaArray.length - 1];

        final double[] szaArray = atmParamLut.getDimension(1).getSequence();
        szaMin = szaArray[0];
        szaMax = szaArray[szaArray.length - 1];

        final double[] raaArray = atmParamLut.getDimension(2).getSequence();
        raaMin = raaArray[0];
        raaMax = raaArray[raaArray.length - 1];

        final double[] hsfArray = atmParamLut.getDimension(3).getSequence();
        hsfMin = hsfArray[0];
        hsfMax = hsfArray[hsfArray.length - 1];

        visArray = atmParamLut.getDimension(4).getSequence();
        visMin = visArray[0];
        visMax = visArray[visArray.length - 1];

        final double[] cwvArray = atmParamLut.getDimension(5).getSequence();
        cwvMin = cwvArray[0];
        cwvMax = cwvArray[cwvArray.length - 1];
    }


    private boolean isOutsideLutRange(double vza, double sza, double raa, double hsf, double vis, double cwv) {
        return (vza < vzaMin || vza > vzaMax) ||
                (sza < szaMin || sza > szaMax) ||
                (raa < raaMin || raa > raaMax) ||
                (hsf < hsfMin || hsf > hsfMax) ||
                (vis < visMin || vis > visMax) ||
                (cwv < cwvMin || cwv > cwvMax);
    }

}
