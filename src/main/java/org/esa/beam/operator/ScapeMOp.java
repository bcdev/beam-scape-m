package org.esa.beam.operator;

import Jama.Matrix;
import org.esa.beam.ScapeMMode;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.io.LutAccess;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.math.LookupTable;

import java.io.IOException;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM", version = "1.0-SNAPSHOT",
                  authors = "Tonio Fincke, Olaf Danne",
                  copyright = "(c) 2013 Brockmann Consult",
                  description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm.")
public class ScapeMOp extends Operator {
    public static final String VERSION = "1.0-SNAPSHOT";

    @Parameter(description = "AOT processing mode", defaultValue = "SHORT")
    private ScapeMMode aotMode;

    @Parameter(description = "CWV processing mode", defaultValue = "SHORT")
    private ScapeMMode cwvMode;

    @SourceProduct(description = "MERIS L1B product")
    private Product sourceProduct;

    // Auxdata
    private LookupTable atmParamLut;

    private double vzaMin;
    private double vzaMax;
    private double szaMin;
    private double szaMax;
    private double raaMin;
    private double raaMax;
    private double hsfMin;
    private double hsfMax;
    private double visMin;
    private double visMax;
    private double cwvMin;
    private double cwvMax;


    @Override
    public void initialize() throws OperatorException {

        setTargetProduct(createScapeMProduct());
        readAuxdata();

        // transform IDL procedure 'derive_AtmPar_Refl' to Java...:

        // 1. AOT retrieval on 30x30km cells:
        //      - set 30x30km cell grid. residuals in last column/row are filled to previous cells,
        //          so cell indices should be e.g. like this for y dim in case of SCENE_HEIGHT=305 (same for x dim):
        //          IDL> PRINT, y_end_arr
        //                  29          59          89         119         149
        //                  179         209         239         269         304
        //      - apply 'cloud mask 2' over all pixels in cell
        //      - consider only cells with more than 35% cloud-free land pixels
        //      - compute VISIBILITY for those cells  (--> 'interpol_lut')
        //      - refinement of AOT retrieval:
        //          ** in given cell, consider only land pixels defined from 'cloud mask 1'
        //          ** from these, determine 5 'reference pixels' ( --> 'extract_ref_pixels')
        //             and derive visibility from them ( --> 'inversion_MERIS_AOT')
        //      - fill gaps (<35% land): interpolate from surrounding cells ( --> 'fill_gaps_vis_new')
        //      - spatial smoothing by cubic convolution
        //      - conversion visibility --> AOT550

        // 2. CWV retrieval:
        //      - minimise 'Merit' function with Brent method ( --> 'ZBRENT', 'chisq_merisWV')
        //
        // 3. Reflectance retrieval:
        //      - from LUT (--> 'interpol_lut' called from  'derive_AtmPar_Refl'), but using
        //        different approach for bands 2, 11, 15 (see paper)
        //

    }

    private Product createScapeMProduct() {
        final int width = sourceProduct.getSceneRasterWidth();
        final int height = sourceProduct.getSceneRasterHeight();

        Product scapeMProduct = new Product("SCAPE_M",
                                            "SCAPE_M",
                                            width,
                                            height);

        ProductUtils.copyMetadata(sourceProduct, scapeMProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, scapeMProduct);
        ProductUtils.copyFlagBands(sourceProduct, scapeMProduct, true);
        ProductUtils.copyMasks(sourceProduct, scapeMProduct);

        return scapeMProduct;
    }


    void readAuxdata() {
        try {
            atmParamLut = LutAccess.getAtmParmsLookupTable();
        } catch (IOException e) {
            throw new OperatorException(e.getMessage());
        }

        final double[] vzaArray = atmParamLut.getDimension(0).getSequence();
        vzaMin = vzaArray[0];
        vzaMax = vzaArray[vzaArray.length - 1];

        final double[] szaArray = atmParamLut.getDimension(1).getSequence();
        szaMin = szaArray[0];
        szaMax = szaArray[szaArray.length - 1];

        final double[] raaArray = atmParamLut.getDimension(2).getSequence();
        raaMin = raaArray[0];
        raaMax = raaArray[raaArray.length - 1];

        final double[] hsfArray = atmParamLut.getDimension(3).getSequence();
        hsfMin = hsfArray[0];
        hsfMax = hsfArray[hsfArray.length - 1];

        final double[] visArray = atmParamLut.getDimension(4).getSequence();
        visMin = visArray[0];
        visMax = visArray[visArray.length - 1];

        final double[] cwvArray = atmParamLut.getDimension(5).getSequence();
        cwvMin = cwvArray[0];
        cwvMax = cwvArray[cwvArray.length - 1];
    }

    private boolean isOutsideLutRange(double vza, double sza, double raa, double hsf, double vis, double cwv) {
        return (vza < vzaMin || vza > vzaMax) ||
               (sza < szaMin || sza > szaMax) ||
               (raa < raaMin || raa > raaMax) ||
               (hsf < hsfMin || hsf > hsfMax) ||
               (vis < visMin || vis > visMax) ||
               (cwv < cwvMin || cwv > cwvMax);
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMOp.class);
        }
    }
}
