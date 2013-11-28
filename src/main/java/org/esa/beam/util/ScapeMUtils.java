package org.esa.beam.util;

import org.esa.beam.util.math.MathUtils;

import java.util.Calendar;

/**
 * SCAPE-M utility class
 *
 * @author olafd
 */
public class ScapeMUtils {

    /**
     * Calculation of the variability of the solar constant during the year.
     * (Java implementation of 6S Fortran routine 'varsol').
     *
     * @param doy - day of year
     * @return dSol - multiplicative factor to apply to the mean value of the solar constant
     */
    public static double varSol(int doy) {
        double om = 0.9856 * (doy - 4) * MathUtils.DTOR;
        double dSol = Math.sqrt(Math.pow(1. - 0.01673 * Math.cos(om), 2.0));

        return dSol;
    }

    public static Double[] getAsDoubleArray(double[] src) {
        Double[] result = new Double[src.length];
        int index = 0;
        for (double d : src) {
            result[index++] = new Double(d);
        }
        return result;
    }

    public static double getMeanDouble1D(double[] src) {
        double mean = 0.0;
        for (int i = 0; i < src.length; i++) {
            mean += src[i];
        }
        return mean / src.length;
    }

    public static double getMeanDouble1D(Double[] src) {
        double mean = 0.0;
        for (int i = 0; i < src.length; i++) {
            mean += src[i].doubleValue();
        }
        return mean / src.length;
    }


    public static double getMinimumDouble1D(double[] src) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < src.length; i++) {
            if (src[i] < min) {
                min = src[i];
            }
        }
        return min;
    }

    public static int getMinimumIndexDouble1D(double[] src) {
        double min = Double.MAX_VALUE;
        int minIndex = -1;
        for (int i = 0; i < src.length; i++) {
            if (src[i] < min) {
                min = src[i];
                minIndex = i;
            }
        }
        return minIndex;
    }

    public static double getStdevDouble1D(double[] src) {
        double diffSqr = 0.0;
        double mean = getMeanDouble1D(src);
        for (int i = 0; i < src.length; i++) {
            diffSqr += Math.pow(src[i] - mean, 2.0);
        }
        return Math.sqrt(diffSqr/(src.length-1));
    }

    public static double getStdevDouble1D(Double[] src) {
        double diffSqr = 0.0;
        double mean = getMeanDouble1D(src);
        for (int i = 0; i < src.length; i++) {
            diffSqr += Math.pow(src[i].doubleValue() - mean, 2.0);
        }
        return Math.sqrt(diffSqr/(src.length-1));
    }



}