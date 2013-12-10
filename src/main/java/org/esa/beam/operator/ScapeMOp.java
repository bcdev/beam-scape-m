package org.esa.beam.operator;

import org.esa.beam.ScapeMConstants;
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

    @Parameter(description = "Compute over all water (not just over lakes)",
               label = "Compute over all water (not just over lakes)",
               defaultValue = "false")
    private boolean computeOverWater;

    @SourceProduct(description = "MERIS L1B product")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    protected ScapeMLut scapeMLut;

    private String demName = ScapeMConstants.DEFAULT_DEM_NAME;

    private Product cloudProduct;
    private Product cellVisibilityProduct;
    private Product gapFilledVisibilityProduct;
    private Product smoothedVisibilityProduct;
    private Product aotProduct;
    private Product atmosCorrProduct;


    @Override
    public void initialize() throws OperatorException {
        readAuxdata();

        try {
            // todo: this is only for ONE test product to compare with LG dimap input!! check if start time is null.
            if (sourceProduct.getStartTime() == null) {
                sourceProduct.setStartTime(ProductData.UTC.parse("20060819", "yyyyMMdd"));
                sourceProduct.setEndTime(ProductData.UTC.parse("20060819", "yyyyMMdd"));
                //                int year = sourceProduct.getName()... // todo continue
            }
        } catch (Exception e) {
            throw new OperatorException("could not add missing product start/end times: ", e);
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
        visParams.put("computeOverWater", computeOverWater);
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
//        atmosCorrProduct = smoothedVisibilityProduct;
        Map<String, Product> atmosCorrInput = new HashMap<String, Product>(4);
        atmosCorrInput.put("source", sourceProduct);
        atmosCorrInput.put("cloud", cloudProduct);
        atmosCorrInput.put("visibility", smoothedVisibilityProduct);
        Map<String, Object> atmosCorrParams = new HashMap<String, Object>(1);
        atmosCorrParams.put("scapeMLut", scapeMLut);
        atmosCorrParams.put("computeOverWater", computeOverWater);
        atmosCorrProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMAtmosCorrOp.class),
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
