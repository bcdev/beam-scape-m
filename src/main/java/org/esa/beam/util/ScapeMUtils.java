package org.esa.beam.util;

import org.esa.beam.operator.AtmParamLookupTable;
import org.esa.beam.operator.Luts;
import org.esa.beam.util.math.LookupTable;

import javax.imageio.stream.ImageInputStream;
import java.io.IOException;

/**
 * SCAPE-M utility class
 *
 * @author olafd
 */
public class ScapeMUtils {

    /**
     * reads an Atmospheric parameters LUT (IDL breadboard procedure 'read_lut')
     * * This LUT is equivalent to the original IDL LUT:
     * for i = 0, nm_avs - 1 do begin
     * for j = 0, nm_asl - 1 do $
     * for k = 0, nm_azm - 1 do $
     * for ii = 0, nm_hsf - 1 do $
     * for jj = 0, nm_vis - 1 do $
     * for kk = 0, nm_wv - 1 do $
     * readu, 1, aux
     * for ind = 0, nm_par - 1 do lut[ind, i, j, k, ii, jj, kk, *] = aux[*, ind]
     * A LUT value can be accessed with
     * lut.getValue(new double[]{vzaValue, szaValue, raaValue, hsfValue, visValue, cwvValue, wvlValue, parameterValue});
     *
     * @return LookupTable
     * @throws java.io.IOException when failing to real LUT data
     */
    public static AtmParamLookupTable getAtmParmsLookupTable() throws IOException {
        ImageInputStream iis = Luts.getAtmParamLutData();
        try {
            // read LUT dimensions and values
            float[] wvl = Luts.readDimension(iis);
            int nWvl = wvl.length;
            float[] cwv = Luts.readDimension(iis);
            int nCwv = cwv.length;
            float[] vis = Luts.readDimension(iis);
            int nVis = vis.length;
            float[] hsf = Luts.readDimension(iis);
            int nHsf = hsf.length;
            float[] raa = Luts.readDimension(iis);
            int nRaa = raa.length;
            float[] sza = Luts.readDimension(iis);
            int nSza = sza.length;
            float[] vza = Luts.readDimension(iis);
            int nVza = vza.length;

            float[] parameters = new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f};
            int nParameters = parameters.length;

            float[] lut = new float[nParameters * nVza * nSza * nRaa * nHsf * nVis * nCwv * nWvl];

            for (int iVza = 0; iVza < nVza; iVza++) {
                for (int iSza = 0; iSza < nSza; iSza++) {
                    for (int iRaa = 0; iRaa < nRaa; iRaa++) {
                        for (int iHsf = 0; iHsf < nHsf; iHsf++) {
                            for (int iVis = 0; iVis < nVis; iVis++) {
                                for (int iCwv = 0; iCwv < nCwv; iCwv++) {
                                    for (int iWvl = 0; iWvl < nWvl; iWvl++) {
                                        for (int iParams = 0; iParams < nParameters; iParams++) {
                                            int i = iParams + nParameters * (iWvl + nWvl * (iCwv + nCwv * (iVis + nVis * (iHsf + nHsf * (iRaa + nRaa * (iSza + nSza * (iVza + nVza * iWvl)))))));
                                            lut[i] = iis.readFloat();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // store in original sequence (see breadboard: loop over bd, jj, ii, k, j, i in GA_read_lut_AOD
            AtmParamLookupTable atmParamLut = new AtmParamLookupTable();
            atmParamLut.setLut(new LookupTable(lut, vza, sza, raa, hsf, vis, cwv, wvl, parameters));
            atmParamLut.setWvl(wvl);
            return atmParamLut;
        } finally {
            iis.close();
        }
    }


}