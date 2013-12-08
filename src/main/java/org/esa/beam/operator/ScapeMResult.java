package org.esa.beam.operator;

/**
 * Container holding a water vapour result from Brent computation in AC part.
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMResult {
    double[][] wv;
    double wvInf;
    private double[][][] refl;

    public double[][] getWv() {
        return wv;
    }

    public double getWvPixel(int x, int y) {
        return wv[x][y];
    }

    public void setWv(double[][] wv) {
        this.wv = wv;
    }

    public void setWvPixel(int x, int y, double wvValue) {
        wv[x][y] = wvValue;
    }

    public double getWvInf() {
        return wvInf;
    }

    public void setWvInf(double wvInf) {
        this.wvInf = wvInf;
    }

    public double[][][] getRefl() {
        return refl;
    }

    public double getReflPixel(int bandId, int x, int y) {
        return refl[bandId][x][y];
    }

    public void setRefl(double[][][] refl) {
        this.refl = refl;
    }

    public void setReflPixel(int bandId, int x, int y, double reflValue) {
        refl[bandId][x][y] = reflValue;
    }
}
