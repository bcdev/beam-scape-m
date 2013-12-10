package org.esa.beam.util;

import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.TiePointGrid;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.Tile;

import java.awt.*;

/**
 * SCAPE-M utility class
 *
 * @author olafd
 */
public class ScapeMUtils {

    public static double getSumDouble1D(Double[] src) {
        double sum = 0.0;
        for (Double d : src) {
            sum += d;
        }
        return sum;
    }

    public static double getMeanDouble1D(double[] src) {
        return getSumDouble1D(src) / src.length;
    }

    public static double getMeanDouble1D(Double[] src) {
        return getSumDouble1D(src) / src.length;
    }

    public static double getMinimumDouble1D(double[] src) {
        double min = Double.MAX_VALUE;
        for (double d : src) {
            if (d < min) {
                min = d;
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
        for (double d : src) {
            diffSqr += Math.pow(d - mean, 2.0);
        }
        return Math.sqrt(diffSqr/(src.length-1));
    }

    private static double getSumDouble1D(double[] src) {
        double sum = 0.0;
        for (double d : src) {
            sum += d;
        }
        return sum;
    }

    public static double getMeanDouble2D(double[][] wv) {
        // todo implement
        return 0;  //To change body of created methods use File | Settings | File Templates.
    }

}