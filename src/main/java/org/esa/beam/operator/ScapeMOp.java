package org.esa.beam.operator;

import org.esa.beam.ScapeMConstants;
import org.esa.beam.ScapeMMode;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.FlagCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.gpf.GPF;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.algorithms.scapem.FubScapeMOp;
import org.esa.beam.io.LutAccess;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.BitSetter;
import org.esa.beam.util.ProductUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM", version = "1.0-SNAPSHOT",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2013 Brockmann Consult",
        description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm.")
public class ScapeMOp extends MerisBasisOp implements Constants {
    public static final String VERSION = "1.0-SNAPSHOT";

    @Parameter(description = "AOT processing mode", defaultValue = "SHORT")
    private ScapeMMode aotMode;

    @Parameter(description = "CWV processing mode", defaultValue = "SHORT")
    private ScapeMMode cwvMode;

    @Parameter(description = "DEM name", defaultValue = "GETASSE30")
    private String demName;

    @SourceProduct(description = "MERIS L1B product")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    protected ScapeMLut scapeMLut;

    private Product cloudProduct;
    private Product cellVisibilityProduct;
    private Product gapFilledVisibilityProduct;
    private Product smoothedVisibilityProduct;
    private Product aotProduct;
    private Product atmosCorrProduct;


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
        //             and derive visibility for cell from them ( --> 'inversion_MERIS_AOT')
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

        readAuxdata();

        try {
            // todo: this is only for ONE test product to compare with LG dimap input!! check if start time is null.
            if (sourceProduct.getStartTime() == null) {
                sourceProduct.setStartTime(ProductData.UTC.parse("20060819", "yyyyMMdd"));
                sourceProduct.setEndTime(ProductData.UTC.parse("20060819", "yyyyMMdd"));
                //                int year = sourceProduct.getName()... // todo continue
            }
        } catch (Exception e) {
            throw new OperatorException("could not load L2Auxdata", e);
        }

        // get the cloud product from Idepix...
        Map<String, Product> idepixInput = new HashMap<String, Product>(4);
        idepixInput.put("source", sourceProduct);
        Map<String, Object> cloudParams = new HashMap<String, Object>(1);
        cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FubScapeMOp.class), cloudParams, idepixInput);

        // get the cell visibility/AOT product...
        Map<String, Product> cellVisibilityInput = new HashMap<String, Product>(4);
        cellVisibilityInput.put("source", sourceProduct);
        cellVisibilityInput.put("cloud", cloudProduct);
        Map<String, Object> visParams = new HashMap<String, Object>(1);
        visParams.put("scapeMLut", scapeMLut);
        // this is a product with grid resolution, but having equal visibility values over a cell (30x30km)
        // (follows the IDL implementation)
        cellVisibilityProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVisibilityOp.class), visParams, cellVisibilityInput);

        // fill gaps...
//        gapFilledVisibilityProduct = cellVisibilityProduct;
        try {
            gapFilledVisibilityProduct = ScapeMGapFill.gapFill(cellVisibilityProduct);
        } catch (IOException e) {
            throw new OperatorException(e.getMessage(), e);
        }

        // smooth...       // todo: insert smoothing finally after reflectances are verified against IDL
        smoothedVisibilityProduct = gapFilledVisibilityProduct;
//        Map<String, Product> smoothInput = new HashMap<String, Product>(4);
//        smoothInput.put("source", gapFilledVisibilityProduct);
//        smoothedVisibilityProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMSmoothOp.class), GPF.NO_PARAMS, smoothInput);

        // convert visibility to AOT
//        aotProduct = smoothedVisibilityProduct;
        Map<String, Product> aotConvertInput = new HashMap<String, Product>(4);
        aotConvertInput.put("source", sourceProduct);
        aotConvertInput.put("visibility", smoothedVisibilityProduct);
        Map<String, Object> aotConvertParams = new HashMap<String, Object>(1);
        aotConvertParams.put("scapeMLut", scapeMLut);
        aotProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVis2AotOp.class),
                aotConvertParams, aotConvertInput);

        // derive CWV...
        // derive reflectance...
        atmosCorrProduct = smoothedVisibilityProduct;
        Map<String, Product> atmosCorrInput = new HashMap<String, Product>(4);
        atmosCorrInput.put("source", sourceProduct);
        atmosCorrInput.put("cloud", cloudProduct);
        atmosCorrInput.put("visibility", smoothedVisibilityProduct);
        Map<String, Object> atmosCorrParams = new HashMap<String, Object>(1);
        atmosCorrParams.put("scapeMLut", scapeMLut);
        aotProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVis2AotOp.class),
                atmosCorrParams, atmosCorrInput);


        targetProduct = atmosCorrProduct;
        ProductUtils.copyFlagBands(cloudProduct, targetProduct, true);
        ProductUtils.copyMasks(cloudProduct, targetProduct);
        ProductUtils.copyBand(ScapeMConstants.AOT550_BAND_NAME, aotProduct, atmosCorrProduct, true);
    }

    private void readAuxdata() {
        try {
            scapeMLut = new ScapeMLut(LutAccess.getAtmParmsLookupTable());
        } catch (IOException e) {
            // todo
            throw new OperatorException(e.getMessage());
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMOp.class);
        }
    }
}
