package org.esa.beam.util;

import org.junit.Test;

import java.util.Calendar;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class ScapeMUtilsTest {

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


}
