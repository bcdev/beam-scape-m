package org.esa.beam.operator;

import org.esa.beam.io.LutAccess;
import org.esa.beam.math.MvFunction;
import org.esa.beam.meris.l2auxdata.Constants;

/**
 * Representation of TOA minimization function ('minim_TOA' from IDL breadboard)
 *
 * @author olafd
 */
public class ToaMinimization implements MvFunction, Constants {
    private double[] chiSquare;
    private double visLowerLim;
    private double visUpperLim;
    private double[] visArray;
    private double[][] lpwArray;
    private double[][] etwArray;
    private double[][] sabArray;

    @Override
    public double f(double[] x) {
        // todo: implement 'minim_TOA' from IDL here



        double vis = x[10];
        visUpperLim = visArray[visArray.length-1];
        boolean xVectorInvalid = false;
        for (int i = 0; i < x.length; i++) {
            if (x[i] < 0.0) {
                xVectorInvalid = true;
                break;
            }
        }

        if (!xVectorInvalid && vis >= visLowerLim && vis < visUpperLim) {
            int index = 0;
            for (int i = 0; i < visArray.length; i++) {
                if (vis >= visArray[i]) {

                }
            }
        } else {
            return 5.E+8; // ???
        }

//        FUNCTION minim_TOA, x
//        common pow, wl_center_inv, vis_old, num_pix, wl_center
//        common fits, toa, chi_sq
//        common inversion, lpw, etw, sab, ro_veg, ro_sue, weight, ref_pix, vis_lim
//        common static, lpw_int, etw_int, sab_int
//        common lut_gr, vis_gr, wv_gr, hs_gr, dim_vis, dim_wv, dim_hs
//
//                ;print, 'entering "minim_TOA"'
//        wh = where(x lt 0., cont_neg)
//        vis = x[10]
//        ;stop
//        if cont_neg eq 0. and vis ge vis_lim and vis lt vis_gr[dim_vis - 1] then begin
//
//        if vis ne vis_old then begin
//                wh = where(vis ge vis_gr)
//        vis_inf = wh[n_elements(wh) - 1]
//
//        delta = 1. / (vis_gr[vis_inf + 1] - vis_gr[vis_inf])
//
//        lpw_int = ((lpw[*, vis_inf + 1] - lpw[*, vis_inf]) * vis + lpw[*, vis_inf] * vis_gr[vis_inf + 1] - $
//        lpw[*, vis_inf + 1] * vis_gr[vis_inf]) * delta
//
//        etw_int = ((etw[*, vis_inf + 1] - etw[*, vis_inf]) * vis + etw[*, vis_inf] * vis_gr[vis_inf + 1] - $
//        etw[*, vis_inf + 1] * vis_gr[vis_inf]) * delta
//
//        sab_int = ((sab[*, vis_inf + 1] - sab[*, vis_inf]) * vis + sab[*, vis_inf] * vis_gr[vis_inf + 1] - $
//        sab[*, vis_inf + 1] * vis_gr[vis_inf]) * delta
//                endif
//
//        for i = 0, num_pix - 1 do begin
//                surf_refl = x[2 * i] * ro_veg + x[2 * i + 1] * ro_sue
//        toa[i, *] = lpw_int + surf_refl * etw_int  / !pi / (1 - sab_int * surf_refl)
//        chi_sq[i] = total((wl_center_inv * (ref_pix[i,*] - toa[i,*])) ^ 2.) ;ref_pix ya corregidos con (1.e-4 * d * d)
//        endfor
//
//                minim = total(weight * chi_sq)
//                ;stop
//        ;  if vis ne vis_old then stop
//                vis_old = vis
//
//        endif else minim = 5.e+8
//
//        ;print, 'leaving "minim_TOA"'
//        return, minim
//
//                END




        return 0;
    }

    public double[] getChiSquare() {
        return chiSquare;
    }

    public void setVisLowerLim(double visLowerLim) {
        this.visLowerLim = visLowerLim;
    }

    public void setVisUpperLim(double visUpperLim) {
        this.visUpperLim = visUpperLim;
    }

    public void setVisArray(double[] visArray) {
        this.visArray = visArray;
    }

    public void setLpwArray(double[][] lpwArray) {
        this.lpwArray = lpwArray;
    }

    public void setEtwArray(double[][] etwArray) {
        this.etwArray = etwArray;
    }

    public void setSabArray(double[][] sabArray) {
        this.sabArray = sabArray;
    }
}
