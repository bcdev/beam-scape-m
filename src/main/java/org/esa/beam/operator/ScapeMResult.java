package org.esa.beam.operator;

/**
 * Container holding a water vapour result from Brent computation in AC part.
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMResult {
    double[][] wv;
    private double[][][] refl;

    public ScapeMResult(int l1BandNum, int width, int height) {
        refl = new double[l1BandNum][width][height];
        wv = new double[width][height];

        for (int i = 0; i < l1BandNum; i++) {
            refl[i] = new double[width][height];
            for (int j = 0; j < width; j++) {
                refl[i][j] = new double[height];
            }
        }

        for (int j = 0; j < width; j++) {
            wv[j] = new double[height];
        }
    }

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
