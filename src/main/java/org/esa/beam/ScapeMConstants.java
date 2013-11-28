package org.esa.beam;

/**
 * Scape-M Constants
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMConstants {

    public final static float[] MERIS_WAVELENGTHS = {
            412.545f, 442.401f, 489.744f, 509.7f, 559.634f,
            619.62f, 664.64f, 680.902f, 708.426f, 753.472f,
            761.606f, 778.498f, 864.833f, 884.849f, 899.86f
    };

    public final static double[] MERIS_CAL_COEFFS = {
            0.009474839,
            0.010603477,
            0.01158433,
            0.01070568,
            0.00932934,
            0.008188124,
            0.0068480745,
            0.0069379713,
            0.0063089724,
            0.008664634,
            0.0088729495,
            0.0036326132,
            0.0035543414,
            0.0061037163,
            0.005430559
    };

    public final static double solIrr7 = 1424.7742;
    public final static double solIrr9 = 1225.6102;

    public final static int NUM_REF_PIXELS = 5;

    public final static int REF_PIXELS_HIGH = 0;
    public final static int REF_PIXELS_MEDIUM = 1;
    public final static int REF_PIXELS_LOW = 2;

    public final static double[][] RHO_VEG_ALL = {
            {0.0235,0.0382,0.0319,0.0342,0.0526,0.0425,0.0371,0.0369,0.0789,0.3561,0.3698,0.3983,0.4248,0.4252,0.4254},
            {0.0206,0.04120,0.0445,0.0498,0.0728,0.0821,0.0847,0.0870,0.1301,0.1994,0.2020,0.2074,0.2365,0.2419,0.2459},
            {0.0138,0.0158,0.0188,0.021,0.0395,0.0279,0.0211,0.0206,0.0825,0.2579,0.2643,0.2775,0.3201,0.3261,0.3307}
    };

    public static final double POWELL_FTOL = 1.E-4;
}
