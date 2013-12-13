package org.esa.beam.util;

import javax.media.jai.JAI;
import javax.media.jai.RenderedOp;
import javax.media.jai.operator.MeanDescriptor;
import javax.media.jai.operator.SubtractDescriptor;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

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

    // todo: check if still needed
    public static double getImageMeanValue(RenderedImage image) {
        // retrieve mean of source image of given band
        final RenderedOp meanOp = MeanDescriptor.create(image, null, 1, 1, null);
        final double[] mean = (double[]) meanOp.getProperty("mean");
        return mean[0];
    }

    public static RenderedOp getImagesDifference(RenderedImage image1, RenderedImage image2) {
        // retrieve pixelwise differences of two images
        return SubtractDescriptor.create(image1, image2, null);
    }

    public static RenderedOp getImagesAbsolute(RenderedImage image1) {
        // retrieve new image with absolute of pixels of source image

        // Create a ParameterBlock with the source image.
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(image1);
        return JAI.create("absolute", pb);
    }

}