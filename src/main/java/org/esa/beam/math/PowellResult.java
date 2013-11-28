package org.esa.beam.math;

/**
 * Object holding the Powell algorithm solution and its uncertainty (a pure container).
 *
 * @author olafd
 */
public class PowellResult {
    private double[] minLocation;    // the location of the minimum (solution of Powell algorithm)
    private double fMin;             // the function value at the location of the minimum (solution of Powell algorithm)
    private double[] chiSquare;      // the uncertainty vector

    public double[] getMinLocation() {
        return minLocation;
    }

    public void setMinLocation(double[] minLocation) {
        this.minLocation = minLocation;
    }

    public double getfMin() {
        return fMin;
    }

    public void setfMin(double fMin) {
        this.fMin = fMin;
    }

    public double[] getChiSquare() {
        return chiSquare;
    }

    public void setChiSquare(double[] chiSquare) {
        this.chiSquare = chiSquare;
    }
}
