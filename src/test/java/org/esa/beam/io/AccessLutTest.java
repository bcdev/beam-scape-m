package org.esa.beam.io;

import junit.framework.TestCase;
import org.esa.beam.util.math.LookupTable;

import java.io.IOException;


public class AccessLutTest extends TestCase {
    public void testAtmParamLut() throws IOException {
        LookupTable lut = LutAccess.getAtmParmsLookupTable();
        assertNotNull(lut);

        assertEquals(8, lut.getDimensionCount());

        final double[] wvlArr = lut.getDimension(7).getSequence();
        final int nWvl = wvlArr.length;
        assertEquals(15, nWvl);
        assertEquals(412.545f, wvlArr[0], 1.E-3);
        assertEquals(442.401f, wvlArr[1], 1.E-3);
        assertEquals(899.86f, wvlArr[14], 1.E-3);

        final double[] paramArr = lut.getDimension(6).getSequence();
        final int nParameters = paramArr.length;
        assertEquals(7, nParameters);     //  Parameters
        assertEquals(1.0, paramArr[0], 1.E-4);
        assertEquals(2.0, paramArr[1], 1.E-4);
        assertEquals(3.0, paramArr[2], 1.E-4);
        assertEquals(5.0, paramArr[4], 1.E-4);
        assertEquals(6.0, paramArr[5], 1.E-4);
        assertEquals(7.0, paramArr[6], 1.E-4);

        final double[] cwvArr = lut.getDimension(5).getSequence();
        final int nCwv = cwvArr.length;
        assertEquals(6, nCwv);     //  CWV
        assertEquals(0.3, cwvArr[0], 1.E-4);
        assertEquals(1.0, cwvArr[1], 1.E-4);
        assertEquals(1.5, cwvArr[2], 1.E-4);
        assertEquals(2.0, cwvArr[3], 1.E-4);
        assertEquals(2.7, cwvArr[4], 1.E-4);
        assertEquals(5.0, cwvArr[5], 1.E-4);

        final double[] visArr = lut.getDimension(4).getSequence();
        final int nVis = visArr.length;
        assertEquals(7, nVis);
        assertEquals(10.0, visArr[0], 1.E-3);
        assertEquals(15.0, visArr[1], 1.E-3);
        assertEquals(23.0, visArr[2], 1.E-3);
        assertEquals(35.0, visArr[3], 1.E-3);
        assertEquals(60.0, visArr[4], 1.E-3);
        assertEquals(100.0, visArr[5], 1.E-3);
        assertEquals(180.0, visArr[6], 1.E-3);

        final double[] hsfArr = lut.getDimension(3).getSequence();
        final int nHsf = hsfArr.length;
        assertEquals(3, nHsf);     //  HSF
        assertEquals(0.0, hsfArr[0], 1.E-3);
        assertEquals(0.7, hsfArr[1], 1.E-3);
        assertEquals(2.5, hsfArr[2], 1.E-3);

        final double[] raaArr = lut.getDimension(2).getSequence();
        final int nRaa = raaArr.length;
        assertEquals(7, nRaa);     //  RAA
        assertEquals(0.0, raaArr[0], 1.E-4);
        assertEquals(25.0, raaArr[1], 1.E-4);
        assertEquals(50.0, raaArr[2], 1.E-4);
        assertEquals(85.0, raaArr[3], 1.E-4);
        assertEquals(120.0, raaArr[4], 1.E-4);
        assertEquals(155.0, raaArr[5], 1.E-4);
        assertEquals(180.0, raaArr[6], 1.E-4);

        final double[] szaArr = lut.getDimension(1).getSequence();
        final int nSza = szaArr.length;
        assertEquals(6, nSza);     //  SZA
        assertEquals(0.0, szaArr[0], 1.E-4);
        assertEquals(10.0, szaArr[1], 1.E-4);
        assertEquals(20.0, szaArr[2], 1.E-4);
        assertEquals(35.0, szaArr[3], 1.E-4);
        assertEquals(50.0, szaArr[4], 1.E-4);
        assertEquals(65.0, szaArr[5], 1.E-4);

        final double[] vzaArr = lut.getDimension(0).getSequence();
        final int nVza = vzaArr.length;
        assertEquals(6, nVza);     //  VZA
        assertEquals(0.0, vzaArr[0], 1.E-4);
        assertEquals(9.0, vzaArr[1], 1.E-4);
        assertEquals(18.0, vzaArr[2], 1.E-4);
        assertEquals(27.0, vzaArr[3], 1.E-4);
        assertEquals(36.0, vzaArr[4], 1.E-4);
        assertEquals(45.0, vzaArr[5], 1.E-4);

        // first values in LUT
        double[] coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[0], wvlArr[0]};
        double value = lut.getValue(coord);
        assertEquals(0.009594766, value, 1.E-4);

        coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[0], wvlArr[1]};
        value = lut.getValue(coord);
        assertEquals(0.008080291, value, 1.E-4);

        coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[0], wvlArr[2]};
        value = lut.getValue(coord);
        assertEquals(0.006640377, value, 1.E-4);

        coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[0], wvlArr[14]};
        value = lut.getValue(coord);
        assertEquals(0.0007679362, value, 1.E-4);

        coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[1], wvlArr[0]};
        value = lut.getValue(coord);
        assertEquals(0.03709091, value, 1.E-4);

        coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[1], wvlArr[1]};
        value = lut.getValue(coord);
        assertEquals(0.04535859, value, 1.E-4);

        coord = new double[]{vzaArr[0], szaArr[0], raaArr[0], hsfArr[0], visArr[0], cwvArr[0], paramArr[1], wvlArr[14]};
        value = lut.getValue(coord);
        assertEquals(0.05023599, value, 1.E-4);


        // values somewhere inside LUT:
        coord = new double[]{vzaArr[3], szaArr[4], raaArr[6], hsfArr[0], visArr[3], cwvArr[1], paramArr[2], wvlArr[3]};
        value = lut.getValue(coord);
        assertEquals(0.0308434, value, 1.E-4);

        coord = new double[]{vzaArr[1], szaArr[2], raaArr[4], hsfArr[2], visArr[4], cwvArr[5], paramArr[3], wvlArr[12]};
        value = lut.getValue(coord);
        assertEquals(0.0520935, value, 1.E-4);

        coord = new double[]{vzaArr[2], szaArr[5], raaArr[2], hsfArr[1], visArr[2], cwvArr[3], paramArr[4], wvlArr[8]};
        value = lut.getValue(coord);
        assertEquals(0.0849858, value, 1.E-4);


        // last values in LUT:
        coord = new double[]{vzaArr[5], szaArr[5], raaArr[6], hsfArr[2], visArr[6], cwvArr[5], paramArr[6], wvlArr[12]};
        value = lut.getValue(coord);
        assertEquals(0.02610051, value, 1.E-4);

        coord = new double[]{vzaArr[5], szaArr[5], raaArr[6], hsfArr[2], visArr[6], cwvArr[5], paramArr[6], wvlArr[13]};
        value = lut.getValue(coord);
        assertEquals(0.02497919, value, 1.E-4);

        coord = new double[]{vzaArr[5], szaArr[5], raaArr[6], hsfArr[2], visArr[6], cwvArr[5], paramArr[6], wvlArr[14]};
        value = lut.getValue(coord);
        assertEquals(0.02453506, value, 1.E-4);
    }
}
