package org.esa.beam.operator;

import com.bc.ceres.core.ProgressMonitor;
import com.bc.ceres.glevel.MultiLevelImage;
import org.esa.beam.ScapeMConstants;
import org.esa.beam.dataio.envisat.EnvisatConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.dataop.dem.ElevationModelDescriptor;
import org.esa.beam.framework.dataop.dem.ElevationModelRegistry;
import org.esa.beam.framework.dataop.resamp.Resampling;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.idepix.util.IdepixUtils;
import org.esa.beam.meris.brr.HelperFunctions;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.meris.l2auxdata.L2AuxDataProvider;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import javax.media.jai.*;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.*;
import java.awt.image.Kernel;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Calendar;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm: cell visibility retrieval part.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM.smooth", version = "1.0-SNAPSHOT",
                  authors = "Tonio Fincke, Olaf Danne",
                  copyright = "(c) 2013 Brockmann Consult",
                  description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm: cell visibility retrieval part.")
public class ScapeMSmoothOp extends MerisBasisOp implements Constants {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    @Override
    public void initialize() throws OperatorException {

        createTargetProduct();

    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());
        targetProduct.setPreferredTileSize(sourceProduct.getPreferredTileSize());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        KernelJAI kernel1 = KernelJAI.GRADIENT_MASK_SOBEL_HORIZONTAL; // todo: check what kind of kernel we need!
        float[] kernelData = new float[]{
                0.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 1.0f,
                0.0f, 1.0f, 0.0f
        };
        KernelJAI kernel2 = new KernelJAI(3, 3, kernelData);

        Band b = sourceProduct.getBand(ScapeMVisibilityOp.VISIBILITY_BAND_NAME);
        RenderedImage sourceImage = b.getSourceImage();
        RenderedOp targetImage = JAI.create("convolve", sourceImage, kernel2);

        // todo: maybe the source product will later be on 'cell grid' (1 value per cell), then we will need
        // something like this instead:
//        RenderedOp targetImage = ScaleDescriptor.create(sourceImage,
//                                                              30.0f,
//                                                              30.0f,
//                                                              0.0f, 0.0f,
//                                                              Interpolation.getInstance(
//                                                                      Interpolation.INTERP_BICUBIC), null);

        Band targetBand = ProductUtils.copyBand(sourceProduct.getName(), sourceProduct, targetProduct, false);
        targetBand.setSourceImage(targetImage);
    }


    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMSmoothOp.class);
        }
    }
}
