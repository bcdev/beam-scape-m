package org.esa.beam.operator;


import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.io.LutAccess;
import org.esa.beam.math.Powell;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.util.CellSample;
import org.esa.beam.util.CellSampleComparator;
import org.esa.beam.util.ScapeMUtils;
import org.esa.beam.util.Varsol;
import org.esa.beam.util.math.LookupTable;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class representing visibility part of SCAPE-M algorithm
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMVisibility implements Constants {

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

    private double[] visArrayLUT;


    public ScapeMVisibility(L2AuxData auxData) {
        l2AuxData = auxData;
        readAuxdata();
    }

    /**
     * Determines if cell is regarded as 'clear land' : > 35% must not be water or cloud
     * <p/>
     * // todo describe parameters
     *
     * @param rect       - cell rectangle
     * @param cloudFlags - cloud flags
     * @return boolean
     */
    boolean isCellClearLand(Rectangle rect,
                            Tile cloudFlags,
                            double percentage) {
        int countClearLand = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if (cloudFlags.getSampleBit(x, y, 4)) {   // mask_land_all !!
                    countClearLand++;
                }
            }
        }
        return countClearLand / (rect.getWidth() * rect.getHeight()) > percentage;
    }

    /**
     * Returns the elevation mean value (in km) over all land pixels in a 30x30km cell
     *
     * @param hSurfCell - hsurf single values
     * @return hsurf cell mean value
     * @throws Exception
     */
    double getHsurfMeanCell(double[][] hSurfCell,
                            Tile cloudFlags) throws Exception {

        double hsurfMean = 0.0;
        int hsurfCount = 0;
        Rectangle rect = cloudFlags.getRectangle();
        for (int y = 0; y < hSurfCell[0].length; y++) {
            for (int x = 0; x < hSurfCell.length; x++) {
                if (!(Double.isNaN(hSurfCell[x][y]))) {
                    if (cloudFlags.getSampleBit(rect.x + x, rect.y + y, 4)) {   // mask_land_all !!
                        hsurfMean += hSurfCell[x][y];
                        hsurfCount++;
                    }
                }
            }
        }

        return hsurfMean / hsurfCount;    // km
    }

    double[][] getHsurfArrayCell(Rectangle rect,
                                 GeoCoding geoCoding,
                                 Tile demTile) throws Exception {

        double[][] hSurf = new double[rect.width][rect.height];
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if (geoCoding.canGetGeoPos()) {
                    double demValue = demTile.getSampleDouble(x, y);
                    hSurf[x - rect.x][y - rect.y] = Math.max(hsfMin, 0.001 * demValue);
                } else {
                    hSurf[x - rect.x][y - rect.y] = hsfMin;
                }
            }
        }
        return hSurf;
    }

    double[][] getHsurfArrayCell(Rectangle rect,
                                 GeoCoding geoCoding,
                                 ElevationModel elevationModel) throws Exception {

        double[][] hSurf = new double[rect.width][rect.height];
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    hSurf[x - rect.x][y - rect.y] = Math.max(hsfMin, 0.001 * elevationModel.getElevation(geoPos));
                } else {
                    hSurf[x - rect.x][y - rect.y] = hsfMin;
                }
            }
        }
        return hSurf;
    }


    double getCosSzaMeanCell(double[][] cosSzaCell,
                             Tile cloudFlags) throws Exception {

        double cosSzaMean = 0.0;
        int cosSzaCount = 0;
        Rectangle rect = cloudFlags.getRectangle();
        for (int y = 0; y < cosSzaCell[0].length; y++) {
            for (int x = 0; x < cosSzaCell.length; x++) {
                if (!(Double.isNaN(cosSzaCell[x][y]))) {
                    if (cloudFlags.getSampleBit(rect.x + x, rect.y + y, 4)) {   // mask_land_all !!
                        cosSzaMean += cosSzaCell[x][y];
                        cosSzaCount++;
                    }
                }
            }
        }

        return cosSzaMean / cosSzaCount;
    }

    double[][] getCosSzaArrayCell(Rectangle rect,
                                  Tile szaTile) throws Exception {

        double[][] cosSza = new double[rect.width][rect.height];
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                final double sza = szaTile.getSampleDouble(x, y);
                cosSza[x - rect.x][y - rect.y] = Math.cos(sza * MathUtils.DTOR);
            }
        }

        return cosSza;
    }

    double getToaMinCell(double[][] toaArrayCell) throws Exception {
        double toaMin = Double.MAX_VALUE;
        for (int y = 0; y < toaArrayCell[0].length; y++) {
            for (int x = 0; x < toaArrayCell.length; x++) {
                if (!(Double.isNaN(toaArrayCell[x][y])) && toaArrayCell[x][y] > 0.0) {
                    if (toaArrayCell[x][y] < toaMin) {
                        toaMin = toaArrayCell[x][y];
                    }
                }
            }
        }
        return toaMin;
    }

    double[][] getToaArrayCell(Tile radianceTile,
                               Rectangle rect,
                               int doy) throws Exception {

        double[][] toa = new double[rect.width][rect.height];
        double varSol = Varsol.getVarSol(doy);
        final double solFactor = varSol * varSol * 1.E-4;

        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                toa[x - rect.x][y - rect.y] = radianceTile.getSampleDouble(x, y) * solFactor;

//                GeoPos geoPos = null;
//                if (geoCoding.canGetGeoPos() && !(Double.isNaN(radianceTile.getSampleDouble(x, y)) && radianceTile.getSampleDouble(x, y) > 0.0)) {
//                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
//                    try {
//                        if (!classifier.isWater(geoPos.lat, geoPos.lon)) {
//                            toa[x - rect.x][y - rect.y] = radianceTile.getSampleDouble(x, y) * solFactor;
//                        } else {
//                            toa[x - rect.x][y - rect.y] = radianceTile.getSampleDouble(x, y) * solFactor;
////                            toa[x - rect.x][y - rect.y] = Double.NaN;   // todo: IDL does not do this,
////                            but we think it is stragne to use water pixels e.g. for minimum detection
////                            which is used for the land retrievals
//                        }
//                    } catch (IOException ignore) {
//                    }
//                } else {
//                    toa[x - rect.x][y - rect.y] = radianceTile.getSampleDouble(x, y) * solFactor;
////                    toa[x - rect.x][y - rect.y] = Double.NaN;      // todo see above
//                }
            }
        }
        return toa;
    }

    /**
     * gets the visibility for a 30x30km cell
     * <p/>
     * // todo describe parameters
     *
     * @param targetRect
     * @param toaArrayCell         - toa refl single values
     * @param toaMinCell           - toa min cell value
     * @param vza                  - vza
     * @param sza                  - sza
     * @param raa                  - raa
     * @param hsurfArrayCell       - hsurf single values
     * @param hsurfMeanCell        - hsurf mean cell value
     * @param cosSzaArrayCell      - cosSza singe values
     * @param cosSzaMeanCell       - cosSza mean cell value
     * @param cellIsClear45Percent - true if cell is > 45% clea land
     * @return visibility
     */
    public double getCellVisibility(Rectangle targetRect, double[][][] toaArrayCell,
                                    double[] toaMinCell, double vza, double sza, double raa,
                                    double[][] hsurfArrayCell,
                                    double hsurfMeanCell,
                                    double[][] cosSzaArrayCell, // mus_il_sub
                                    double cosSzaMeanCell, // mus_il
                                    boolean cellIsClear45Percent) {

        final int nVis = visArrayLUT.length;
        final double[] step = {1.0, 0.1};
        final double wvInit = 2.0;

        if (targetRect.x == 540 && targetRect.y == 60) {
            System.out.println("targetRect = " + targetRect);
        }
        double vis = visMin - step[0];
        for (int i = 0; i <= 1; i++) {
            if (i == 1) {
                vis = Math.max(vis - step[0], visMin);
            }
            boolean repeat = true;
            while (((vis + step[i]) < visMax) && (repeat == true)) {
                vis += step[i];
                double[][] fInt = LutAccess.interpolAtmParamLut(atmParamLut, vza, sza, raa, hsurfMeanCell, vis, wvInit);
                repeat = false;
                for (int j = 0; j < nVis; j++) {
                    if (toaMinCell[j] <= fInt[j][0]) {
                        repeat = true;
                    }
                }
            }
        }

        double visVal = vis - step[1];

        if (cellIsClear45Percent) {
            double[][] refPixelsBand0 =
                    extractRefPixels(targetRect, 0, hsurfArrayCell, hsurfMeanCell, cosSzaArrayCell, cosSzaMeanCell, toaArrayCell);
            if (targetRect.x == 30 && targetRect.y == 0) {
//                System.out.println("targetRect = " + targetRect);
            }
            if (refPixelsBand0 != null && refPixelsBand0.length > 0) {
                double[][][] refPixels = new double[L1_BAND_NUM][refPixelsBand0.length][refPixelsBand0[0].length];
                refPixels[0] = refPixelsBand0;

                boolean invalid = false;
                for (int bandId = 1; bandId < L1_BAND_NUM; bandId++) {
                    refPixels[bandId] =
                            extractRefPixels(targetRect, bandId, hsurfArrayCell, hsurfMeanCell, cosSzaArrayCell, cosSzaMeanCell, toaArrayCell);
                    if (refPixels[bandId] == null && refPixels[bandId].length > 0) {
                        invalid = true; // we want valid pixels in ALL bands
                        break;
                    }
                }
                if (targetRect.x == 30 && targetRect.y == 0) {
//                    System.out.println("targetRect = " + targetRect);    // OK until this point!
                }

//                if (!invalid) {
//                    InversionMerisAot inversionMerisAot = new InversionMerisAot();
//                    inversionMerisAot.setVisVal(visVal);
//                    inversionMerisAot.compute(refPixels, vza, sza, raa, hsurfMeanCell, wvInit, cosSzaMeanCell);
//                    visVal = inversionMerisAot.getVisVal();
//                    double visStdev = inversionMerisAot.getVisStdev();       // not needed? does not seem to be further used in IDL
//                    double emCode = inversionMerisAot.getEmCode();           // not needed? does not seem to be further used in IDL
//                }
                if (targetRect.x == 30 && targetRect.y == 0) {
                    System.out.println("targetRect = " + targetRect);
                }
            }
        } else {
            // nothing to do - keep visVal as it was before
        }

        if (targetRect.x == 30 && targetRect.y == 0) {
            System.out.println("targetRect = " + targetRect);
        }
        return visVal;
    }

    /**
     * for given bandId, gives TOA for reference pixels selected from NDVI criteria
     * <p/>
     * // todo describe parameters
     *
     * @param bandId          - band ID
     * @param hsurfArrayCell  - hsurf single values
     * @param hsurfMeanCell   - hsurf mean cell value
     * @param cosSzaArrayCell - cosSza single values
     * @param cosSzaMeanCell  - cosSza mean cell values
     * @param toaArrayCell    - toa single values
     * @return double[][] refPixels = double[selectedPixels][NUM_REF_PIXELS]
     */
    private double[][] extractRefPixels(Rectangle targetRect, int bandId, double[][] hsurfArrayCell, double hsurfMeanCell,
                                        double[][] cosSzaArrayCell, double cosSzaMeanCell, double[][][] toaArrayCell) {

        final int cellWidth = toaArrayCell[0].length;
        final int cellHeight = toaArrayCell[0][0].length;

        final double[] hsurfLim = new double[]{0.8 * hsurfMeanCell, 1.2 * hsurfMeanCell};
        final double[] cosSzaLim = new double[]{0.9 * cosSzaMeanCell, 1.1 * cosSzaMeanCell};

        double[][] ndvi = new double[cellWidth][cellHeight];
        List<CellSample> ndviHighList = new ArrayList<CellSample>();
        List<CellSample> ndviMediumList = new ArrayList<CellSample>();
        List<CellSample> ndviLowList = new ArrayList<CellSample>();
        for (int j = 0; j < cellHeight; j++) {
            for (int i = 0; i < cellWidth; i++) {
                final double toa7 = toaArrayCell[7][i][j] / ScapeMConstants.solIrr7;
                final double toa9 = toaArrayCell[9][i][j] / ScapeMConstants.solIrr9;
                ndvi[i][j] = (toa9 - toa7) / (toa9 + toa7);
                if (hsurfArrayCell[i][j] > hsurfLim[0] && hsurfArrayCell[i][j] < hsurfLim[1] &&
                        cosSzaArrayCell[i][j] > cosSzaLim[0] && cosSzaArrayCell[i][j] < cosSzaLim[1]) {
                    if (ndvi[i][j] >= 0.4 && ndvi[i][j] < 0.9) {
                        ndviHighList.add(new CellSample(i, j, ndvi[i][j]));
                    } else if (ndvi[i][j] >= 0.15 && ndvi[i][j] < 0.4) {
                        ndviMediumList.add(new CellSample(i, j, ndvi[i][j]));
                    } else if (ndvi[i][j] >= 0.09 && ndvi[i][j] < 0.15) {
                        ndviLowList.add(new CellSample(i, j, ndvi[i][j]));
                    }
                }
            }
        }

        // sort NDVIs...
        CellSampleComparator comparator = new CellSampleComparator(true);
        CellSample[] ndviHighSamples = ndviHighList.toArray(new CellSample[ndviHighList.size()]);
        Arrays.sort(ndviHighSamples, comparator);
        CellSample[] ndviMediumSamples = ndviMediumList.toArray(new CellSample[ndviMediumList.size()]);
        Arrays.sort(ndviMediumSamples, comparator);
        CellSample[] ndviLowSamples = ndviLowList.toArray(new CellSample[ndviLowList.size()]);
        Arrays.sort(ndviLowSamples, comparator);

        final int nLim = Math.min(ndviHighSamples.length / 2, ndviMediumSamples.length / 3);
        double[][] refPixels = new double[nLim][ScapeMConstants.NUM_REF_PIXELS];

        if (targetRect.x == 30 && targetRect.y == 0) {
            System.out.println("targetRect = " + targetRect);
        }
        if (ndviMediumSamples.length + 2 >= ScapeMConstants.NUM_REF_PIXELS) {
            //                valid_flg = 1

            for (int i = 0; i < nLim; i++) {
                refPixels[i][0] =
                        toaArrayCell[bandId][ndviHighSamples[2 * i].getCellXIndex()][ndviHighSamples[2 * i].getCellYIndex()];
                refPixels[i][1] =
                        toaArrayCell[bandId][ndviHighSamples[2 * i + 1].getCellXIndex()][ndviHighSamples[2 * i + 1].getCellYIndex()];

                refPixels[i][2] =
                        toaArrayCell[bandId][ndviMediumSamples[2 * i].getCellXIndex()][ndviMediumSamples[2 * i].getCellYIndex()];
                refPixels[i][3] =
                        toaArrayCell[bandId][ndviMediumSamples[2 * i + 1].getCellXIndex()][ndviMediumSamples[2 * i + 1].getCellYIndex()];

                if (i < ndviLowSamples.length) {
                    refPixels[i][4] =
                            toaArrayCell[bandId][ndviLowSamples[i].getCellXIndex()][ndviLowSamples[i].getCellYIndex()];
                } else {
                    refPixels[i][4] =
                            toaArrayCell[bandId][ndviMediumSamples[2 * i + 2].getCellXIndex()][ndviMediumSamples[2 * i + 2].getCellYIndex()];
                }
            }
        } else {
            //                valid_flg = 0
            return null;     // todo: check if this is ok
        }

        return refPixels;
    }

    private void readAuxdata() {
        try {
            atmParamLut = LutAccess.getAtmParmsLookupTable();
        } catch (IOException e) {
            throw new OperatorException(e.getMessage());
        }

        final double[] vzaArray = atmParamLut.getDimension(0).getSequence();
        vzaMin = vzaArray[0] + 0.001;
        vzaMax = vzaArray[vzaArray.length - 1] - 0.001;

        final double[] szaArray = atmParamLut.getDimension(1).getSequence();
        szaMin = szaArray[0] + 0.001;
        szaMax = szaArray[szaArray.length - 1] - 0.001;

        final double[] raaArray = atmParamLut.getDimension(2).getSequence();
        raaMin = raaArray[0];
        raaMax = raaArray[raaArray.length - 1];

        final double[] hsfArray = atmParamLut.getDimension(3).getSequence();
        hsfMin = hsfArray[0] + 0.001;
        hsfMax = hsfArray[hsfArray.length - 1] - 0.001;

        visArrayLUT = atmParamLut.getDimension(4).getSequence();
        visMin = visArrayLUT[0] + 0.001;
        visMax = visArrayLUT[visArrayLUT.length - 1] - 0.001;

        final double[] cwvArray = atmParamLut.getDimension(5).getSequence();
        cwvMin = cwvArray[0] + 0.001;
        cwvMax = cwvArray[cwvArray.length - 1] - 0.001;
    }


    private boolean isOutsideLutRange(double vza, double sza, double raa, double hsf, double vis, double cwv) {
        // todo: check if needed
        return (vza < vzaMin || vza > vzaMax) ||
                (sza < szaMin || sza > szaMax) ||
                (raa < raaMin || raa > raaMax) ||
                (hsf < hsfMin || hsf > hsfMax) ||
                (vis < visMin || vis > visMax) ||
                (cwv < cwvMin || cwv > cwvMax);
    }

    // todo: check if we just need the 'compute' method
    class InversionMerisAot {
        private double visVal;
        private double visStdev;
        private double emCode;

        private void compute(double[][][] refPixels, double vza, double sza, double raa, double hsurfMeanCell, double wvInit, double cosSzaMeanCell) {

            final int numSpec = 2;
            final int numX = numSpec * ScapeMConstants.NUM_REF_PIXELS + 1;

            double[] powellInputInit = new double[numX];
            double visLim = visVal;

            double[][] lpw = new double[L1_BAND_NUM][visArrayLUT.length];
            double[][] etw = new double[L1_BAND_NUM][visArrayLUT.length];
            double[][] sab = new double[L1_BAND_NUM][visArrayLUT.length];

            for (int i = 0; i < visArrayLUT.length; i++) {
                double visArrayVal = Math.max(visMin, Math.min(visMax, visArrayLUT[i]));
                double[][] fInt = LutAccess.interpolAtmParamLut(atmParamLut, vza, sza, raa, hsurfMeanCell, visArrayVal, wvInit);
                for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                    lpw[bandId][i] = fInt[bandId][0];
                    etw[bandId][i] = fInt[bandId][1] * cosSzaMeanCell + fInt[bandId][2];
                    sab[bandId][i] = fInt[bandId][4];
                }
            }

            for (int j = 0; j < ScapeMConstants.NUM_REF_PIXELS; j++) {
                final double ndvi = (refPixels[12][0][j] - refPixels[7][0][j]) / (refPixels[12][0][j] + refPixels[7][0][j]);
                final double ndviMod = 1.3 * ndvi + 0.25;
                powellInputInit[numSpec * j] = Math.max(ndviMod, 0.0);
                powellInputInit[numSpec * j + 1] = Math.max(1.0 - ndviMod, 0.0);
            }
            powellInputInit[numX - 1] = 23.0;

            double[][] xi = new double[numX][numX];
            for (int i = 0; i < numX; i++) {
                xi[i][i] = 1.0;
            }

            final int limRefSets = 1;    // for AOT_time_flg eq 1, see .inp file
            final int nEMVeg = 3;    // for AOT_time_flg eq 1, see .inp file

            final int nRefSets = Math.max(refPixels[0].length, limRefSets);

            double[] visArr = new double[nRefSets];
            double[] fminArr = new double[nEMVeg];
            double[] visArrAux = new double[nEMVeg];

            ToaMinimization toaMinimization = new ToaMinimization(visLim, visArrayLUT, lpw, etw, sab, refPixels, 0.0);
            for (int i = 0; i < nRefSets; i++) {
                for (int j = 0; j < nEMVeg; j++) {
                    double[] xVector = powellInputInit.clone();
                    xVector[10] = visLim + 0.01;
                    double[][] xiInput = xi.clone();

                    final double[] weight = new double[]{2., 2., 1.5, 1.5, 1.};

                    toaMinimization.setEmVegIndex(j);
                    toaMinimization.setWeight(weight);
                    toaMinimization.setRhoVeg(ScapeMConstants.RHO_VEG_ALL[j]);

                    // 'minim_TOA' is the function to be minimized by Powell!
                    // we have to  use this kind of interface:
                    // PowellTestFunction_1 function1 = new PowellTestFunction_1();
                    // double fmin = Powell.fmin(xVector, xi, ftol, function1);
                    double fmin = Powell.fmin(xVector,
                                              xiInput,
                                              ScapeMConstants.POWELL_FTOL,
                                              toaMinimization);
                    double[] chiSqr = toaMinimization.getChiSquare();
                    double chiSqrMean = ScapeMUtils.getMeanDouble1D(chiSqr);

                    int chiSqrOutsideRangeCount = 0;
                    for (int k = 0; k < chiSqr.length; k++) {
                        if (chiSqr[k] > 2.0 * chiSqrMean) {
                            chiSqrOutsideRangeCount++;
                        }
                    }
                    if (chiSqrOutsideRangeCount > 0) {
                        for (int k = 0; k < chiSqr.length; k++) {
                            if (chiSqr[k] > 2.0 * chiSqrMean) {
                                weight[k] = 0.0;
                                toaMinimization.setWeight(weight);
                                toaMinimization.setEmVegIndex(j);
                                toaMinimization.setRhoVeg(ScapeMConstants.RHO_VEG_ALL[j]);
                                fmin = Powell.fmin(xVector,
                                                   xiInput,
                                                   ScapeMConstants.POWELL_FTOL,
                                                   toaMinimization);
                            }
                        }
                    }
                    visArrAux[j] = xVector[numX - 1];
                    fminArr[j] = fmin / (5.0 - chiSqrOutsideRangeCount);
                }
                final int fMinIndex = ScapeMUtils.getMinimumIndexDouble1D(fminArr);
                visArr[i] = visArrAux[fMinIndex];
                emCode = fMinIndex + 1;
            }

            if (nRefSets > 1) {
                double visMean = ScapeMUtils.getMeanDouble1D(visArr);
                setVisStdev(ScapeMUtils.getStdevDouble1D(visArr));

                List<Double> visArrInsideStdevList = new ArrayList<Double>();
                for (int i = 0; i < nRefSets; i++) {
                    if (Math.abs(visArr[i] - visMean) <= 1.5 * visStdev) {
                        visArrInsideStdevList.add(visArr[i]);
                    }
                }
                Double[] visArrInsideStdev = visArrInsideStdevList.toArray(new Double[visArrInsideStdevList.size()]);
                if (visArrInsideStdev.length > 0) {
                    setVisVal(ScapeMUtils.getMeanDouble1D(visArrInsideStdev));
                    setVisStdev(ScapeMUtils.getStdevDouble1D(visArrInsideStdev));
                } else {
                    setVisVal(visMean);
                }
            } else {
                setVisVal(visArr[0]);
                setVisStdev(0.0);
            }

        }

        private double getVisVal() {
            return visVal;
        }

        private void setVisVal(double visVal) {
            this.visVal = visVal;
        }

        private double getVisStdev() {
            return visStdev;
        }

        private void setVisStdev(double visStdev) {
            this.visStdev = visStdev;
        }

        private double getEmCode() {
            return emCode;
        }
    }

}
