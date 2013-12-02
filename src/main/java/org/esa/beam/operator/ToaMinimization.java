package org.esa.beam.operator;

import org.esa.beam.ScapeMConstants;
import org.esa.beam.math.MvFunction;
import org.esa.beam.meris.l2auxdata.Constants;

/**
 * Representation of TOA minimization function ('minim_TOA' from IDL breadboard)
 *
 * @author olafd
 */
public class ToaMinimization implements MvFunction, Constants {
    private double[] chiSquare;

    private double visLowerLim;
    private double[] visArrayLUT;
    private double[][] lpwArray;
    private double[][] etwArray;
    private double[][] sabArray;
    private double[][][] refPixels;
    private int emVegIndex;
    private double[] weight;
    private double visOld;
    private double[] rhoVeg;


    public ToaMinimization(double visLowerLim, double[] visArrayLUT,
                           double[][] lpwArray, double[][] etwArray, double[][] sabArray,
                           double[][][] refPixels, double visOld) {
        this.visLowerLim = visLowerLim;
        this.visArrayLUT = visArrayLUT;
        this.lpwArray = lpwArray;
        this.etwArray = etwArray;
        this.sabArray = sabArray;
        this.refPixels = refPixels;
        this.visOld = visOld;
    }

    @Override
    public double f(double[] x) {
        // todo: implement 'minim_TOA' from IDL here

        double[] lpwInt = new double[L1_BAND_NUM];
        double[] etwInt = new double[L1_BAND_NUM];
        double[] sabInt = new double[L1_BAND_NUM];

        double[] surfRefl = new double[L1_BAND_NUM];
        double[][] toa = new double[L1_BAND_NUM][ScapeMConstants.NUM_REF_PIXELS];
        chiSquare = new double[ScapeMConstants.NUM_REF_PIXELS];

        double vis = x[10];

        final double visUpperLim = visArrayLUT[visArrayLUT.length - 1];
        boolean xVectorInvalid = false;
        for (int i = 0; i < x.length; i++) {
            if (x[i] < 0.0) {
                xVectorInvalid = true;
                break;
            }
        }

        if (!xVectorInvalid && vis >= visLowerLim && vis < visUpperLim) {
            double toaMin = 0.0;
            if (vis != visOld) {
                int visInf = 0;
                for (int i = visArrayLUT.length - 1; i >= 0; i--) {
                    if (vis >= visArrayLUT[i]) {
                        visInf = i;
                    }
                }

                final double delta = visArrayLUT[visInf + 1] - visArrayLUT[visInf];

                for (int i = 0; i < L1_BAND_NUM; i++) {
                    lpwInt[i] = (lpwArray[i][visInf + 1] - lpwArray[i][visInf]) * vis +
                            lpwArray[i][visInf] * visArrayLUT[visInf + 1] -
                            lpwArray[i][visInf + 1] * visArrayLUT[visInf] * delta;
                    etwInt[i] = (etwArray[i][visInf + 1] - etwArray[i][visInf]) * vis +
                            etwArray[i][visInf] * visArrayLUT[visInf + 1] -
                            etwArray[i][visInf + 1] * visArrayLUT[visInf] * delta;
                    sabInt[i] = (sabArray[i][visInf + 1] - sabArray[i][visInf]) * vis +
                            sabArray[i][visInf] * visArrayLUT[visInf + 1] -
                            sabArray[i][visInf + 1] * visArrayLUT[visInf] * delta;
                }
            }
            for (int j = 0; j < ScapeMConstants.NUM_REF_PIXELS; j++) {
                chiSquare[j] = 0.0;
                for (int i = 0; i < L1_BAND_NUM; i++) {
                    surfRefl[i] = x[2 * j] * rhoVeg[i] + x[2 * j + 1] * ScapeMConstants.RHO_SUE[i];
                    toa[i][j] = lpwInt[i] + surfRefl[i] * etwInt[i] / (Math.PI * (1.0 - sabInt[i] * surfRefl[i]));
                    chiSquare[j] += Math.pow(ScapeMConstants.WL_CENTER_INV[i] * (refPixels[i][emVegIndex][j] - toa[i][j]), 2.0);
                }
                toaMin += weight[j] * chiSquare[j];
            }

            visOld = vis;
            return toaMin;

        } else {
            return 5.E+8; // ???   // todo: check suitable 'invalid' value
        }
    }

    public double[] getChiSquare() {
        return chiSquare;
    }

    public void setEmVegIndex(int emVegIndex) {
        this.emVegIndex = emVegIndex;
    }

    public void setWeight(double[] weight) {
        this.weight = weight;
    }


    public void setRhoVeg(double[] rhoVeg) {
        this.rhoVeg = rhoVeg;
    }
}
