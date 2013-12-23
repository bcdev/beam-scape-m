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
                  authors = "Luis Guanter, Olaf Danne",
                  copyright = "(c) 2013 University of Valencia, Brockmann Consult",
                  description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm.")
public class ScapeMOp extends ScapeMMerisBasisOp implements Constants {
    public static final String VERSION = "1.0-SNAPSHOT";

    @Parameter(description = "Compute over all water (not just over lakes)",
               label = "Compute over all water (not just over lakes)",
               defaultValue = "true")
    private boolean computeOverWater;

    @Parameter(description = "If set, use GETASSE30 DEM, otherwise get altitudes from product TPGs",
               label = "Use GETASSE30 DEM",
               defaultValue = "false")
    private boolean useDEM;

    @Parameter(description = "If set, TOA reflectances are written to output product",
                                            label = "Write rhoTOA",
                                            defaultValue = "false")
    private boolean outputRhoToa;

    @Parameter(description = "If set, AC corrected reflectance band 2 (443nm) is written to output product",
               label = "Write 443nm reflectance band",
               defaultValue = "false")
    private boolean outputReflBand2;

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
        checkProductStartStopTimes();
        readAuxdata();

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
        visParams.put("useDEM", useDEM);
        // this is a product with grid resolution, but having equal visibility values over a cell (30x30km)
        // (follows the IDL implementation)
        cellVisibilityProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVisibilityOp.class), visParams, cellVisibilityInput);

        // fill gaps...
        try {
            gapFilledVisibilityProduct = ScapeMGapFill.gapFill(cellVisibilityProduct);
        } catch (IOException e) {
            throw new OperatorException(e.getMessage(), e);
        }

        Map<String, Product> smoothInput = new HashMap<String, Product>(4);
        smoothInput.put("source", sourceProduct);
        smoothInput.put("gapFilled", gapFilledVisibilityProduct);
        smoothedVisibilityProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMSmoothFillOp.class), GPF.NO_PARAMS, smoothInput);

        // convert visibility to AOT
        Map<String, Product> aotConvertInput = new HashMap<String, Product>(4);
        aotConvertInput.put("source", sourceProduct);
        aotConvertInput.put("visibility", smoothedVisibilityProduct);
        Map<String, Object> aotConvertParams = new HashMap<String, Object>(1);
        aotConvertParams.put("scapeMLut", scapeMLut);
        aotProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVis2AotOp.class),
                                       aotConvertParams, aotConvertInput);

        // derive CWV...
        // derive reflectance...
        Map<String, Product> atmosCorrInput = new HashMap<String, Product>(4);
        atmosCorrInput.put("source", sourceProduct);
        atmosCorrInput.put("cloud", cloudProduct);
        atmosCorrInput.put("visibility", smoothedVisibilityProduct);
        Map<String, Object> atmosCorrParams = new HashMap<String, Object>(1);
        atmosCorrParams.put("scapeMLut", scapeMLut);
        atmosCorrParams.put("computeOverWater", computeOverWater);
        atmosCorrParams.put("useDEM", useDEM);
        atmosCorrParams.put("outputRhoToa", outputRhoToa);
        atmosCorrParams.put("outputReflBand2", outputReflBand2);
        atmosCorrProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMAtmosCorrOp.class),
                                             atmosCorrParams, atmosCorrInput);

        targetProduct = atmosCorrProduct;
        ProductUtils.copyFlagBands(cloudProduct, targetProduct, true);
        ProductUtils.copyMasks(cloudProduct, targetProduct);
        ProductUtils.copyBand(ScapeMConstants.AOT550_BAND_NAME, aotProduct, atmosCorrProduct, true);
    }

    private void checkProductStartStopTimes() {
        try {
            if (sourceProduct.getStartTime() == null || sourceProduct.getEndTime() == null) {
                // we assume a regular L1b product name such as
                // MER_RR__1PNBCM20060819_073317_000000542050_00264_23364_0735.N1:
                final String ymd = sourceProduct.getName().substring(14,22);
                final String hms = sourceProduct.getName().substring(23,29);
                sourceProduct.setStartTime(ProductData.UTC.parse(ymd + " " + hms, "yyyyMMdd HHmmss"));
                sourceProduct.setEndTime(ProductData.UTC.parse(ymd + " " + hms, "yyyyMMdd HHmmss"));
            }
        } catch (Exception e) {
            throw new OperatorException("could not add missing product start/end times: ", e);
        }
    }

    private void readAuxdata() {
        try {
            scapeMLut = new ScapeMLut(LutAccess.getAtmParmsLookupTable());
        } catch (IOException e) {
            throw new OperatorException("Cannot read atmospheric LUT: ", e);
        }
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMOp.class);
        }
    }
}
