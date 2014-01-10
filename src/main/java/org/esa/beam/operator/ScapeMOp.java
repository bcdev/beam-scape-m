package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.swing.progress.DialogProgressMonitor;
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

import javax.swing.*;
import java.awt.*;
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

    @Parameter(description = "Use constant water vapour value of 2 g/cm^2 to save processing time",
               label = "Use constant water vapour value of 2 g/cm^2",
               defaultValue = "false")
    private boolean useConstantWv;

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

    @SourceProduct(alias = "MERIS_L1b", description = "MERIS L1B product")
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
//        readAuxdata();

        final long t1 = System.currentTimeMillis();
        readAuxdata();
        final long t2 = System.currentTimeMillis();
        getLogger().info("ScapeMOp.readAuxdata took "+(t2-t1)+" ms");

        // get the cloud product from Idepix...
        Map<String, Product> idepixInput = new HashMap<String, Product>(4);
        idepixInput.put("source", sourceProduct);
        Map<String, Object> cloudParams = new HashMap<String, Object>(1);
        cloudProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(FubScapeMOp.class), cloudParams, idepixInput);

        final long t3 = System.currentTimeMillis();
        getLogger().info("step 1 "+(t3-t2)+" ms");

        // get the cell visibility/AOT product...
        // this is a product with grid resolution, but having equal visibility values over a cell (30x30km)
        // (follows the IDL implementation)
        final ScapeMVisibilityOp scapeMVisibilityOp = new ScapeMVisibilityOp();
        scapeMVisibilityOp.setSourceProduct("source", sourceProduct);
        scapeMVisibilityOp.setSourceProduct("cloud", cloudProduct);
        scapeMVisibilityOp.setParameter("computeOverWater", computeOverWater);
        scapeMVisibilityOp.setParameter("useDEM", useDEM);
        scapeMVisibilityOp.setScapeMLut(scapeMLut);
        cellVisibilityProduct  = scapeMVisibilityOp.getTargetProduct();

//        Map<String, Product> cellVisibilityInput = new HashMap<String, Product>(4);
//        cellVisibilityInput.put("source", sourceProduct);
//        cellVisibilityInput.put("cloud", cloudProduct);
//        Map<String, Object> visParams = new HashMap<String, Object>(1);
//        visParams.put("scapeMLut", scapeMLut);
//        visParams.put("computeOverWater", computeOverWater);
//        visParams.put("useDEM", useDEM);
//        cellVisibilityProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVisibilityOp.class), visParams, cellVisibilityInput);

        final long t4 = System.currentTimeMillis();
        getLogger().info("step 2 "+(t4-t3)+" ms");

        // fill gaps...
//        gapFilledVisibilityProduct = cellVisibilityProduct; // test!!
        try {
            gapFilledVisibilityProduct = ScapeMGapFill.gapFill(cellVisibilityProduct);
        } catch (IOException e) {
            throw new OperatorException(e.getMessage(), e);
        }

        Map<String, Product> smoothInput = new HashMap<String, Product>(4);
        smoothInput.put("source", sourceProduct);
        smoothInput.put("gapFilled", gapFilledVisibilityProduct);
        smoothedVisibilityProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMSmoothFillOp.class), GPF.NO_PARAMS, smoothInput);

        final long t5 = System.currentTimeMillis();
        getLogger().info("step 3 "+(t5-t4)+" ms");

//        // convert visibility to AOT
        final ScapeMVis2AotOp scapeMVis2AotOp = new ScapeMVis2AotOp();
        scapeMVis2AotOp.setSourceProduct("source", sourceProduct);
        scapeMVis2AotOp.setSourceProduct("visibility", smoothedVisibilityProduct);
        scapeMVis2AotOp.setScapeMLut(scapeMLut);
        aotProduct = scapeMVis2AotOp.getTargetProduct();

//        Map<String, Product> aotConvertInput = new HashMap<String, Product>(4);
//        aotConvertInput.put("source", sourceProduct);
//        aotConvertInput.put("visibility", smoothedVisibilityProduct);
//        Map<String, Object> aotConvertParams = new HashMap<String, Object>(1);
//        aotConvertParams.put("scapeMLut", scapeMLut);
//        aotProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMVis2AotOp.class),
//                                       aotConvertParams, aotConvertInput);

        final long t6 = System.currentTimeMillis();
        getLogger().info("step 4 "+(t6-t5)+" ms");

        // derive CWV...
        // derive reflectance...
//        Map<String, Product> atmosCorrInput = new HashMap<String, Product>(4);
//        atmosCorrInput.put("source", sourceProduct);
//        atmosCorrInput.put("cloud", cloudProduct);
//        atmosCorrInput.put("visibility", smoothedVisibilityProduct);
//        Map<String, Object> atmosCorrParams = new HashMap<String, Object>(1);
//        atmosCorrParams.put("scapeMLut", scapeMLut);
//        atmosCorrParams.put("computeOverWater", computeOverWater);
//        atmosCorrParams.put("useDEM", useDEM);
//        atmosCorrParams.put("outputRhoToa", outputRhoToa);
//        atmosCorrParams.put("outputReflBand2", outputReflBand2);
//        atmosCorrProduct = GPF.createProduct(OperatorSpi.getOperatorAlias(ScapeMAtmosCorrOp.class),
//                                             atmosCorrParams, atmosCorrInput);

        final ScapeMAtmosCorrOp scapeMAtmosCorrOp = new ScapeMAtmosCorrOp();
        scapeMAtmosCorrOp.setSourceProduct("source", sourceProduct);
        scapeMAtmosCorrOp.setSourceProduct("cloud", cloudProduct);
        scapeMAtmosCorrOp.setSourceProduct("visibility", smoothedVisibilityProduct);
        scapeMAtmosCorrOp.setParameter("computeOverWater", computeOverWater);
        scapeMAtmosCorrOp.setParameter("useDEM", useDEM);
        scapeMAtmosCorrOp.setParameter("outputRhoToa", outputRhoToa);
        scapeMAtmosCorrOp.setParameter("outputReflBand2", outputReflBand2);
        scapeMAtmosCorrOp.setScapeMLut(scapeMLut);
        atmosCorrProduct  = scapeMAtmosCorrOp.getTargetProduct();

        final long t7 = System.currentTimeMillis();
        getLogger().info("step 5 "+(t7-t6)+" ms");

        targetProduct = atmosCorrProduct;
//        targetProduct = cellVisibilityProduct;
//        targetProduct = gapFilledVisibilityProduct;
//        targetProduct = smoothedVisibilityProduct;
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
