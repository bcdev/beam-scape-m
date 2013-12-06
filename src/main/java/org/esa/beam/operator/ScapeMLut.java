package org.esa.beam.operator;

import org.esa.beam.util.math.LookupTable;

/**
 * SCAPE-M lookup table object.
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMLut {
    private LookupTable atmParamLut;

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
    private double[] hsfArrayLUT;

    public ScapeMLut(LookupTable atmParamLut) {
        this.atmParamLut = atmParamLut;
        setVza();
        setSza();
        setRaa();
        setHsf();
        setVis();
        setCwv();
    }

    public LookupTable getAtmParamLut() {
        return atmParamLut;
    }

    public double getVzaMin() {
        return vzaMin;
    }

    public double getVzaMax() {
        return vzaMax;
    }

    public double getSzaMin() {
        return szaMin;
    }

    public double getSzaMax() {
        return szaMax;
    }

    public double getRaaMin() {
        return raaMin;
    }

    public double getRaaMax() {
        return raaMax;
    }

    public double getHsfMin() {
        return hsfMin;
    }

    public double getHsfMax() {
        return hsfMax;
    }

    public double getVisMin() {
        return visMin;
    }

    public double getVisMax() {
        return visMax;
    }

    public double getCwvMin() {
        return cwvMin;
    }

    public double getCwvMax() {
        return cwvMax;
    }

    public double[] getVisArrayLUT() {
        return visArrayLUT;
    }

    public double[] getHsfArrayLUT() {
        return hsfArrayLUT;
    }


    private void setVza() {
        final double[] vzaArray = atmParamLut.getDimension(0).getSequence();
        vzaMin = vzaArray[0] + 0.001;
        vzaMax = vzaArray[vzaArray.length - 1] - 0.001;
    }

    private void setSza() {
        final double[] szaArray = atmParamLut.getDimension(1).getSequence();
        szaMin = szaArray[0] + 0.001;
        szaMax = szaArray[szaArray.length - 1] - 0.001;
    }

    private void setRaa() {
        final double[] raaArray = atmParamLut.getDimension(2).getSequence();
        raaMin = raaArray[0];
        raaMax = raaArray[raaArray.length - 1];
    }

    private void setHsf() {
        hsfArrayLUT = atmParamLut.getDimension(3).getSequence();
        hsfArrayLUT[0] += 0.001;
        hsfMin = hsfArrayLUT[0];
        hsfArrayLUT[hsfArrayLUT.length - 1] -= 0.001;
        hsfMax = hsfArrayLUT[hsfArrayLUT.length - 1];
    }

    private void setVis() {
        visArrayLUT = atmParamLut.getDimension(4).getSequence();
        visArrayLUT[0] += 0.001;
        visMin = visArrayLUT[0];
        visArrayLUT[visArrayLUT.length - 1] -= 0.001;
        visMax = visArrayLUT[visArrayLUT.length - 1];
    }

    private void setCwv() {
        final double[] cwvArray = atmParamLut.getDimension(5).getSequence();
        cwvMin = cwvArray[0] + 0.001;
        cwvMax = cwvArray[cwvArray.length - 1] - 0.001;
    }

}
