package org.esa.beam.math;

/**
 * Interface providing a function.
 *
 * @author Andreas Heckel (USwansea), Olaf Danne
 */
public interface Function {

    /**
     *  Univariate function definition
     *
     *@param  x  - input value
     *@return      return value
     */
    double f(double x);
}
