package org.esa.beam.operator;

import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.Parameter;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.JAI;
import javax.media.jai.KernelJAI;
import javax.media.jai.RenderedOp;
import java.awt.image.RenderedImage;

/**
 * Operator for MERIS atmospheric correction with SCAPE-M algorithm: cell visibility retrieval part.
 *
 * @author Tonio Fincke, Olaf Danne
 */
@OperatorMetadata(alias = "beam.scapeM.smooth", version = "1.0-SNAPSHOT",
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2013 Brockmann Consult",
        internal = true,
        description = "Operator for MERIS atmospheric correction with SCAPE-M algorithm: cell visibility retrieval part.")
public class ScapeMSmoothOp extends MerisBasisOp implements Constants {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;


    @Parameter(description = "JAI Convolve kernel size", defaultValue = "15")
    private int kernelSize;

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
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        KernelJAI kernel = createConvolveKernel();

        Band b = sourceProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        RenderedImage sourceImage = b.getSourceImage();
        RenderedOp targetImage = JAI.create("convolve", sourceImage, kernel);

        // todo: maybe the source product will later be on 'cell grid' (1 value per cell), then we will need
        // something like this instead:
//        RenderedOp targetImage = ScaleDescriptor.create(sourceImage,
//                                                              30.0f,
//                                                              30.0f,
//                                                              0.0f, 0.0f,
//                                                              Interpolation.getInstance(
//                                                                      Interpolation.INTERP_BICUBIC), null);

        Band targetBand = ProductUtils.copyBand(ScapeMConstants.VISIBILITY_BAND_NAME, sourceProduct, targetProduct, false);
        targetBand.setSourceImage(targetImage);
    }

    private KernelJAI createConvolveKernel() {
        float[] kernelMatrix = new float[kernelSize * kernelSize];
        for (int k = 0; k < kernelMatrix.length; k++) {
            kernelMatrix[k] = 1.0f / (kernelSize * kernelSize);
        }
        return new KernelJAI(kernelSize, kernelSize, kernelMatrix);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMSmoothOp.class);
        }
    }
}
