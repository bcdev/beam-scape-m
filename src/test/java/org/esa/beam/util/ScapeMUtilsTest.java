package org.esa.beam.util;

import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class ScapeMUtilsTest {

    double[] x;

    @Before
    public void setUp() throws Exception {
        x = new double[]{65., 63., 67., 64., 68., 62., 70., 66., 68., 67., 69., 71., 66., 65., 70.};
    }

    @Test
    public void testVarSol() {
        int year = 2010;
        int day = 14;

        Calendar cal = Calendar.getInstance();
        cal.set(year, Calendar.MARCH, day);
        int doy = cal.get(Calendar.DAY_OF_YEAR);

        double varSol = ScapeMUtils.varSol(doy);
        assertEquals(0.993734, varSol, 1.E-5);
    }

    @Test
    public void testGetMeanDouble1D() {
        double mean = ScapeMUtils.getMeanDouble1D(x);
        assertEquals(66.7333, mean, 1.E-4);
    }

    @Test
    public void testGetMinimumDouble1D() {
       double min = ScapeMUtils.getMinimumDouble1D(x);
       assertEquals(62.0, min, 1.E-4);
    }

    @Test
    public void testGetMinimumIndexDouble1D() {
        int minIndex = ScapeMUtils.getMinimumIndexDouble1D(x);
        assertEquals(5, minIndex);
    }

    @Test
    public void testGetStdevDouble1D() {
        double stdev = ScapeMUtils.getStdevDouble1D(x);
        assertEquals(2.65832, stdev, 1.E-4);
    }

}
