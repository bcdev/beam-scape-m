package org.esa.beam.operator;

import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.Operator;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "beam.scapeM", version = "1.0-SNAPSHOT",
                  authors = "Tonio Fincke, Olaf Danne",
                  copyright = "(c) 2013 Brockmann Consult",
                  description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm.")
public class ScapeMOp extends Operator {
    public static final String VERSION = "1.0-SNAPSHOT";

    @SourceProduct(description = "MERIS L1B product")
    private Product sourceProduct;

    @Override
    public void initialize() throws OperatorException {

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


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMOp.class);
        }
    }
}
