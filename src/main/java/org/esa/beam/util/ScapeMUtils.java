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
     * @param doy  - day of year
     * @return  dSol - multiplicative factor to apply to the mean value of the solar constant
     */
    public static double varSol(int doy) {
        double om = 0.9856*(doy-4)*MathUtils.DTOR;
        double dSol = Math.sqrt(Math.pow(1. - 0.01673 * Math.cos(om), 2.0));

        return dSol;
    }

}