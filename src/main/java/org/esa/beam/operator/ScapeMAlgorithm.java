package org.esa.beam.operator;


import Stats.LinFit;
import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.exception.NoBracketingException;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.io.LutAccess;
import org.esa.beam.math.Powell;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.*;
import org.esa.beam.util.math.MathUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Class representing SCAPE-M algorithm
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMAlgorithm implements Constants {

    /**
     * Determines if cell is regarded as 'clear land' : > 35% must not be water or cloud
     * <p/>
     * // todo describe parameters
     *
     * @param rect               - cell rectangle
     * @param clearPixelStrategy - clearPixelStrategy
     * @return boolean
     */
    static boolean isCellClearLand(Rectangle rect,
                                   ClearPixelStrategy clearPixelStrategy,
                                   double percentage) {
        int countClearLand = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if (clearPixelStrategy.isValid(x, y)) {   // mask_land_all !!
                    countClearLand++;
                }
            }
        }
        return countClearLand / (rect.getWidth() * rect.getHeight()) > percentage;
    }

    /**
     * Returns the elevation mean value (in km) over all land pixels in a 30x30km cell
     *
     * @param hSurfCell          - hsurf single values
     * @param clearPixelStrategy
     * @return hsurf cell mean value
     * @throws Exception
     */
    static double getHsurfMeanCell(double[][] hSurfCell,
                                   Rectangle rectangle,
                                   ClearPixelStrategy clearPixelStrategy) throws Exception {

        double hsurfMean = 0.0;
        int hsurfCount = 0;
        for (int y = 0; y < hSurfCell[0].length; y++) {
            for (int x = 0; x < hSurfCell.length; x++) {
                if (!(Double.isNaN(hSurfCell[x][y]))) {
                    if (clearPixelStrategy.isValid(rectangle.x + x, rectangle.y + y)) {
                        hsurfMean += hSurfCell[x][y];
                        hsurfCount++;
                    }
                }
            }
        }

        return hsurfMean / hsurfCount;    // km
    }

    static double[][] getHsurfArrayCell(Rectangle rect,
                                        GeoCoding geoCoding,
                                        Tile demTile,
                                        ScapeMLut scapeMLut) throws Exception {

        double[][] hSurf = new double[rect.width][rect.height];
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if (geoCoding.canGetGeoPos()) {
                    double demValue = demTile.getSampleDouble(x, y);
                    hSurf[x - rect.x][y - rect.y] = Math.max(scapeMLut.getHsfMin(), 0.001 * demValue);
                } else {
                    hSurf[x - rect.x][y - rect.y] = scapeMLut.getHsfMin();
                }
                hSurf[x - rect.x][y - rect.y] =
                        Math.max(scapeMLut.getHsfMin(), Math.min(scapeMLut.getHsfMax(), hSurf[x - rect.x][y - rect.y]));
            }
        }
        return hSurf;
    }

    static double[][] getHsurfArrayCell(Rectangle rect,
                                        GeoCoding geoCoding,
                                        ElevationModel elevationModel,
                                        ScapeMLut scapeMLut) throws Exception {

        double[][] hSurf = new double[rect.width][rect.height];
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    hSurf[x - rect.x][y - rect.y] = 0.001 * elevationModel.getElevation(geoPos);
                } else {
                    hSurf[x - rect.x][y - rect.y] = scapeMLut.getHsfMin();
                }
                hSurf[x - rect.x][y - rect.y] =
                        Math.max(scapeMLut.getHsfMin(), Math.min(scapeMLut.getHsfMax(), hSurf[x - rect.x][y - rect.y]));
            }
        }
        return hSurf;
    }

    static double getCosSzaMeanCell(double[][] cosSzaCell,
                                    Tile cloudFlags, boolean computeOverWater) throws Exception {

        double cosSzaMean = 0.0;
        int cosSzaCount = 0;
        Rectangle rect = cloudFlags.getRectangle();
        for (int y = 0; y < cosSzaCell[0].length; y++) {
            for (int x = 0; x < cosSzaCell.length; x++) {
                if (!(Double.isNaN(cosSzaCell[x][y]))) {
                    final boolean considerWater =
                            (computeOverWater || !cloudFlags.getSampleBit(rect.x + x, rect.y + y, ScapeMConstants.CLOUD_OCEAN_BIT));
                    if (!cloudFlags.getSampleBit(rect.x + x, rect.y + y, ScapeMConstants.CLOUD_INVALID_BIT) &&
                            !cloudFlags.getSampleBit(rect.x + x, rect.y + y, ScapeMConstants.CLOUD_CERTAIN_BIT) && considerWater) {
                        cosSzaMean += cosSzaCell[x][y];
                        cosSzaCount++;
                    }
                }
            }
        }

        return cosSzaMean / cosSzaCount;
    }

    static double getCosSzaMeanCell(double[][] cosSzaCell,
                                    Rectangle rect,
                                    ClearPixelStrategy clearPixelStrategy) throws Exception {

        double cosSzaMean = 0.0;
        int cosSzaCount = 0;
        for (int y = 0; y < cosSzaCell[0].length; y++) {
            for (int x = 0; x < cosSzaCell.length; x++) {
                if (!(Double.isNaN(cosSzaCell[x][y]))) {
                    if(clearPixelStrategy.isValid(rect.x + x, rect.y + y)) {
                        cosSzaMean += cosSzaCell[x][y];
                        cosSzaCount++;
                    }
                }
            }
        }

        return cosSzaMean / cosSzaCount;
    }

    static double[][] getCosSzaArrayCell(Rectangle rect,
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

    static double getToaMinCell(double[][] toaArrayCell) throws Exception {
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

    static double[][] getToaArrayCell(Tile radianceTile,
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
////                            but we think it is strange to use water pixels e.g. for minimum detection
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
     *
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
    static double getCellVisibility(double[][][] toaArrayCell,
                                    double[] toaMinCell, double vza, double sza, double raa,
                                    double[][] hsurfArrayCell,
                                    double hsurfMeanCell,
                                    double[][] cosSzaArrayCell, // mus_il_sub
                                    double cosSzaMeanCell, // mus_il
                                    boolean cellIsClear45Percent,
                                    ScapeMLut scapeMLut) {

        final int nVis = scapeMLut.getVisArrayLUT().length;
        final double[] step = {1.0, 0.1};
        final double wvInit = 2.0;

        double vis = scapeMLut.getVisMin() - step[0];
        for (int i = 0; i <= 1; i++) {
            if (i == 1) {
                vis = Math.max(vis - step[0], scapeMLut.getVisMin());
            }
            boolean repeat = true;
            while (((vis + step[i]) < scapeMLut.getVisMax()) && repeat) {
                vis += step[i];
                double[][] fInt = LutAccess.interpolAtmParamLut(scapeMLut.getAtmParamLut(), vza, sza, raa, hsurfMeanCell, vis, wvInit);
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
                    extractRefPixels(0, hsurfArrayCell, hsurfMeanCell, cosSzaArrayCell, cosSzaMeanCell, toaArrayCell);
            if (refPixelsBand0 != null && refPixelsBand0.length > 0) {
                double[][][] refPixels = new double[L1_BAND_NUM][refPixelsBand0.length][refPixelsBand0[0].length];
                refPixels[0] = refPixelsBand0;

                boolean invalid = false;
                for (int bandId = 1; bandId < L1_BAND_NUM; bandId++) {
                    refPixels[bandId] =
                            extractRefPixels(bandId, hsurfArrayCell, hsurfMeanCell, cosSzaArrayCell, cosSzaMeanCell, toaArrayCell);
                    if (refPixels[bandId] == null && refPixels[bandId].length > 0) {
                        invalid = true; // we want valid pixels in ALL bands
                        break;
                    }
                }
                if (!invalid) {
                    // todo: activate and verify later
//                    visVal = computeRefinedVisibility(visVal, refPixels, vza, sza, raa, hsurfMeanCell, wvInit, cosSzaMeanCell, scapeMLut);
                }
            }
        } else {
            // nothing to do - keep visVal as it was before
        }

        visVal = Math.max(scapeMLut.getVisMin(), Math.min(scapeMLut.getVisMax(), visVal));

        return visVal;
    }

    /**
     * for given bandId, gives TOA for reference pixels selected from NDVI criteria
     *
     * @param bandId          - band ID
     * @param hsurfArrayCell  - hsurf single values
     * @param hsurfMeanCell   - hsurf mean cell value
     * @param cosSzaArrayCell - cosSza single values
     * @param cosSzaMeanCell  - cosSza mean cell values
     * @param toaArrayCell    - toa single values
     * @return double[][] refPixels = double[selectedPixels][NUM_REF_PIXELS]
     */
    static double[][] extractRefPixels(int bandId, double[][] hsurfArrayCell, double hsurfMeanCell,
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

    static double getCellAot550(double visibility, double hsurfArrayCell, ScapeMLut scapeMLut) {

        double aot550;

        double lnVis = Math.log(visibility);

        double[][] aCoeff = new double[scapeMLut.getHsfArrayLUT().length][2];
        double[] lnVisGr = new double[scapeMLut.getVisArrayLUT().length];
        double[] lnAotGr = new double[scapeMLut.getVisArrayLUT().length];
        for (int i = 0; i < lnVisGr.length; i++) {
            lnVisGr[i] = Math.log(scapeMLut.getVisArrayLUT()[i]);
        }

        for (int i = 0; i < scapeMLut.getHsfArrayLUT().length; i++) {
            for (int j = 0; j < lnVisGr.length; j++) {
                lnAotGr[j] = Math.log(ScapeMConstants.AOT_GRID[i][j]);
            }
            final LinFit linFit = new LinFit(lnVisGr, lnAotGr, lnVisGr.length);
            aCoeff[i][0] = linFit.getA();
            aCoeff[i][1] = linFit.getB();
        }

        final double hsurf = hsurfArrayCell;
        int hsfIndexToUse = -1;
        for (int i = 0; i < scapeMLut.getHsfArrayLUT().length; i++) {
            if (hsurf >= scapeMLut.getHsfArrayLUT()[i]) {
                hsfIndexToUse = i;
            }
        }
        if (hsfIndexToUse >= 0) {
            double hsp = (hsurf - scapeMLut.getHsfArrayLUT()[hsfIndexToUse]) /
                    (scapeMLut.getHsfArrayLUT()[hsfIndexToUse + 1] - scapeMLut.getHsfArrayLUT()[hsfIndexToUse]);
            double aotTmp1 = Math.exp(aCoeff[hsfIndexToUse][0] + aCoeff[hsfIndexToUse][1] * lnVis);
            double aotTmp2 = Math.exp(aCoeff[hsfIndexToUse + 1][0] + aCoeff[hsfIndexToUse + 1][1] * lnVis);
            aot550 = aotTmp1 + (aotTmp2 - aotTmp1) * hsp;
        } else {
            aot550 = ScapeMConstants.AOT_NODATA_VALUE;
        }

        return aot550;

    }

    private static double computeRefinedVisibility(double visValInput, double[][][] refPixels, double vza, double sza, double raa,
                                                   double hsurfMeanCell, double wvInit, double cosSzaMeanCell,
                                                   ScapeMLut scapeMLut) {

        final int numSpec = 2;
        final int numX = numSpec * ScapeMConstants.NUM_REF_PIXELS + 1;

        double[] powellInputInit = new double[numX];
        double visLim = visValInput;

        double visRefined;

        double[][] lpw = new double[L1_BAND_NUM][scapeMLut.getVisArrayLUT().length];
        double[][] etw = new double[L1_BAND_NUM][scapeMLut.getVisArrayLUT().length];
        double[][] sab = new double[L1_BAND_NUM][scapeMLut.getVisArrayLUT().length];

        for (int i = 0; i < scapeMLut.getVisArrayLUT().length; i++) {
            double visArrayVal = Math.max(scapeMLut.getVisMin(), Math.min(scapeMLut.getVisMax(), scapeMLut.getVisArrayLUT()[i]));
            double[][] fInt = LutAccess.interpolAtmParamLut(scapeMLut.getAtmParamLut(), vza, sza, raa, hsurfMeanCell, visArrayVal, wvInit);
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

        ToaMinimization toaMinimization = new ToaMinimization(visLim, scapeMLut.getVisArrayLUT(), lpw, etw, sab, refPixels, 0.0);
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
        }

        if (nRefSets > 1) {
            double visMean = ScapeMUtils.getMeanDouble1D(visArr);
            double visStdev = ScapeMUtils.getStdevDouble1D(visArr);

            List<Double> visArrInsideStdevList = new ArrayList<Double>();
            for (int i = 0; i < nRefSets; i++) {
                if (Math.abs(visArr[i] - visMean) <= 1.5 * visStdev) {
                    visArrInsideStdevList.add(visArr[i]);
                }
            }
            Double[] visArrInsideStdev = visArrInsideStdevList.toArray(new Double[visArrInsideStdevList.size()]);
            if (visArrInsideStdev.length > 0) {
                visRefined = ScapeMUtils.getMeanDouble1D(visArrInsideStdev);
            } else {
                visRefined = visMean;
            }
        } else {
            visRefined = visArr[0];
        }
        return visRefined;
    }

    public static double[][][] getReflImage(double[][] fInt,
                                            double[][][] toaArray,
                                            double[][] cosSzaArrayCell) {

        final double deltaX =
                1.0 / (ScapeMConstants.MERIS_WAVELENGTHS[13] - ScapeMConstants.MERIS_WAVELENGTHS[12]);

        double[][][] reflImage = new double[3][toaArray[0].length][toaArray[0][0].length];
        for (int i = 0; i < toaArray[0].length; i++) {
            for (int j = 0; j < toaArray[0][0].length; j++) {
                for (int k = 12; k <= 13; k++) {
                    final double xterm = Math.PI * (toaArray[k][i][j] - fInt[k][0]) /
                            (fInt[k][1] * cosSzaArrayCell[i][j] + fInt[k][2]);
                    reflImage[k - 12][i][j] = xterm / (1.0 + fInt[k][4] * xterm);
                }
                reflImage[2][i][j] =
                        ((reflImage[1][i][j] - reflImage[0][i][j]) * ScapeMConstants.MERIS_WAVELENGTHS[14] +
                                reflImage[0][i][j] * ScapeMConstants.MERIS_WAVELENGTHS[13] -
                                reflImage[1][i][j] * ScapeMConstants.MERIS_WAVELENGTHS[12]) * deltaX;
            }
        }

        return reflImage;
    }

    public static ScapeMResult computeAcResult(Rectangle rect,
                                               Tile visibilityTile,
//                                               Tile cloudFlagsTile,
                                               ClearPixelStrategy clearPixelStrategy,
                                               boolean computeOverWater,
                                               double[][][] toaArrayCell,
                                               double[][] hsurfArray,
                                               double[][] cosSzaArray,
                                               double cosSzaMeanCell,
                                               double[][][] reflImg,
                                               Tile radianceTile13,
                                               Tile radianceTile14,
                                               ScapeMLut scapeMLut,
                                               double[][][][] lpw,
                                               double[][][][] e0tw,
                                               double[][][][] ediftw,
                                               double[][][][] tDirD,
                                               double[][][][] sab) {

        final int dimWv = scapeMLut.getCwvArrayLUT().length;
        final int dimVis = scapeMLut.getVisArrayLUT().length;
        final int dimHurf = scapeMLut.getHsfArrayLUT().length;

        ScapeMResult scapeMResult = new ScapeMResult(L1_BAND_NUM, rect.width, rect.height);

        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {

                final double pix1 = reflImg[1][x - rect.x][y - rect.y];
                final double pix2 = reflImg[2][x - rect.x][y - rect.y];
                final double demPix = hsurfArray[x - rect.x][y - rect.y];
                final double visPix = visibilityTile.getSampleDouble(x, y);

                if (clearPixelStrategy.isValid(x, y)) {
                    final double ratioMeris =
                            radianceTile14.getSampleDouble(x, y) / radianceTile13.getSampleDouble(x, y);
                    final double[] reflPix = new double[]{pix1, pix2};

                    int hsIndex = -1;
                    final double[] hsfArrayLUT = scapeMLut.getHsfArrayLUT();
                    for (int i = 0; i < dimHurf; i++) {
                        if (demPix >= hsfArrayLUT[i]) {
                            hsIndex = i;
                        }
                    }
                    final double hsP = (demPix - hsfArrayLUT[hsIndex]) /
                            (hsfArrayLUT[hsIndex + 1] - hsfArrayLUT[hsIndex]);

                    int visIndex = -1;
                    final double[] visArrayLUT = scapeMLut.getVisArrayLUT();
                    for (int i = 0; i < dimVis; i++) {
                        if (visPix >= visArrayLUT[i]) {
                            visIndex = i;
                        }
                    }
                    final double visP = (visPix - visArrayLUT[visIndex]) /
                            (visArrayLUT[visIndex + 1] - visArrayLUT[visIndex]);

                    double[][] lpwSp = new double[L1_BAND_NUM][dimWv];
                    for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                        for (int i = 0; i < dimWv; i++) {
                            lpwSp[bandId][i] = (1.0 - visP) * (1.0 - hsP) * lpw[bandId][i][visIndex][hsIndex] +
                                    hsP * (1.0 - visP) * lpw[bandId][i][visIndex][hsIndex + 1] +
                                    (1.0 - hsP) * visP * lpw[bandId][i][visIndex + 1][hsIndex] +
                                    visP * hsP * lpw[bandId][i][visIndex + 1][hsIndex + 1];
                        }

                    }

                    // adjust etw:
                    double[][][][] etw = new double[L1_BAND_NUM][dimWv][dimVis][dimHurf];
                    final double cosSza = cosSzaArray[x - rect.x][y - rect.y];
                    for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                        for (int i = 0; i < scapeMLut.getCwvArrayLUT().length; i++) {
                            for (int j = 0; j < visArrayLUT.length; j++) {
                                for (int k = 0; k < hsfArrayLUT.length; k++) {
                                    // (1.- tdir_d * mus) * mun_term_arr[ind]:
                                    final double sum1 = (1.0 - tDirD[bandId][i][j][k] * cosSzaMeanCell) * 1.0;
                                    // tdir_d * mus_il_arr[ind] :
                                    final double sum2 = tDirD[bandId][i][j][k] * cosSza;
                                    // e0tw * mus_il_arr[ind] :
                                    final double sum3 = e0tw[bandId][i][j][k] * cosSza;

                                    etw[bandId][i][j][k] = sum3 + ediftw[bandId][i][j][k] * (sum2 + sum1);
                                }
                            }
                        }
                    }

                    double[][] etwSp = new double[L1_BAND_NUM][dimWv];
                    double[][] sabSp = new double[L1_BAND_NUM][dimWv];
                    for (int bandId = 0; bandId < L1_BAND_NUM; bandId++) {
                        for (int i = 0; i < dimWv; i++) {
                            etwSp[bandId][i] = (1.0 - visP) * (1.0 - hsP) * etw[bandId][i][visIndex][hsIndex] +
                                    hsP * (1.0 - visP) * etw[bandId][i][visIndex][hsIndex + 1] +
                                    (1.0 - hsP) * visP * etw[bandId][i][visIndex + 1][hsIndex] +
                                    visP * hsP * etw[bandId][i][visIndex + 1][hsIndex + 1];
                            sabSp[bandId][i] = (1.0 - visP) * (1.0 - hsP) * sab[bandId][i][visIndex][hsIndex] +
                                    hsP * (1.0 - visP) * sab[bandId][i][visIndex][hsIndex + 1] +
                                    (1.0 - hsP) * visP * sab[bandId][i][visIndex + 1][hsIndex] +
                                    visP * hsP * sab[bandId][i][visIndex + 1][hsIndex + 1];
                        }

                    }

                    double[][][] parAtmH = new double[3][2][dimWv];
                    for (int i = 0; i < 2; i++) {
                        for (int j = 0; j < dimWv; j++) {
                            parAtmH[0][i][j] = lpwSp[i + 13][j];
                            parAtmH[1][i][j] = etwSp[i + 13][j];
                            parAtmH[2][i][j] = sabSp[i + 13][j];
                        }
                    }

                    // now water vapour:
                    // all numbers in this test taken from IDL test run, cellIndexX=1, cellIndexY=0
                    WaterVapourFunction wvFunction = new WaterVapourFunction();
                    wvFunction.setMerisRatio(ratioMeris);
                    wvFunction.setWvGr2(scapeMLut.getCwvArrayLUT());
                    wvFunction.setParAtmH(parAtmH);
                    wvFunction.setReflPix(reflPix);

                    final double wvLower = scapeMLut.getCwvMin();
                    final double wvUpper = scapeMLut.getCwvMax();

                    BrentSolver brentSolver = new BrentSolver(ScapeMConstants.FTOL);
                    double wvResult;
                    if (x == 480 && y == 0) {
//                    System.out.println("rect = " + rect);
                    }
                    try {
                        wvResult = brentSolver.solve(ScapeMConstants.MAXITER, wvFunction,
                                                     wvLower, wvUpper);
                    } catch (NoBracketingException e) {
                        wvResult = ScapeMConstants.WV_INIT;
//                    e.printStackTrace();
//                        System.out.println("rect, x, y = " + rect + "//" + x + "," + y);
                    }
                    scapeMResult.setWvPixel(x - rect.x, y - rect.y, wvResult);

                    double[] lpwAc = new double[L1_BAND_NUM];
                    double[] etwAc = new double[L1_BAND_NUM];
                    double[] sabAc = new double[L1_BAND_NUM];
                    final int wvInf = wvFunction.getWvInf();
                    final double wvP = wvFunction.getWvP();
                    for (int i = 0; i < L1_BAND_NUM; i++) {
                        if (i != 10 && i != 14) {
                            lpwAc[i] = lpwSp[i][wvInf] + wvP * (lpwSp[i][wvInf + 1] - lpwSp[i][wvInf]);
                            etwAc[i] = etwSp[i][wvInf] + wvP * (etwSp[i][wvInf + 1] - etwSp[i][wvInf]);
                            sabAc[i] = sabSp[i][wvInf] + wvP * (sabSp[i][wvInf + 1] - sabSp[i][wvInf]);

                            final double xTerm =
                                    Math.PI * (toaArrayCell[i][x - rect.x][y - rect.y] - lpwAc[i]) / etwAc[i];
                            final double refl = xTerm / (1.0 + sabAc[i] * xTerm);
                            scapeMResult.setReflPixel(i, x - rect.x, y - rect.y, refl);
                        }
                    }
                } else {
                    // invalid due to one or more of the above cases
                    for (int i = 0; i < L1_BAND_NUM; i++) {
                        scapeMResult.setReflPixel(i, x - rect.x, y - rect.y, ScapeMConstants.AC_NODATA);
                    }
                    scapeMResult.setWvPixel(x - rect.x, y - rect.y, ScapeMConstants.AC_NODATA);
                }
            }

        }
        return scapeMResult;
    }
}
