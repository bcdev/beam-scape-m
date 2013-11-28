package org.esa.beam.math;

/**
 * Implements test function 1:  f(x0, x1) = (x0 + 2.0*x1) * EXP(-x0*x0 -x1*x1)
 *
 * @author olafd
 */
public class PowellTestFunction_1 implements MvFunction {

    @Override
    public double f(double[] x) {
        // implement test function 1:  f(x0, x1) = (x0 + 2.0*x1) * EXP(-x0*x0 -x1*x1)

        if (x.length != 2) {
            return -1;
        } else {
            return (x[0] + 2.0*x[1]) * Math.exp(-x[0]*x[0] -x[1]*x[1]);
        }
    }
}
