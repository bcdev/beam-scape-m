package org.esa.beam.operator;

import org.esa.beam.util.math.LookupTable;

/**
 * wrapper object to hold an Atmospheric parameter lookup table as in IDL breadboard
 * todo: taken from GA, maybe we don't need this wrapping here...
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class AtmParamLookupTable {
    private LookupTable lut;
    private float[] wvl;

    public LookupTable getLut() {
        return lut;
    }

    public void setLut(LookupTable lut) {
        this.lut = lut;
    }

    public float[] getWvl() {
        return wvl;
    }

    public void setWvl(float[] wvl) {
        this.wvl = wvl;
    }
}
