package org.esa.beam.operator;

import org.esa.beam.ScapeMConstants;
import org.esa.beam.framework.datamodel.Band;
import org.esa.beam.framework.datamodel.Product;
import org.esa.beam.framework.gpf.OperatorException;
import org.esa.beam.framework.gpf.OperatorSpi;
import org.esa.beam.framework.gpf.annotations.OperatorMetadata;
import org.esa.beam.framework.gpf.annotations.SourceProduct;
import org.esa.beam.framework.gpf.annotations.TargetProduct;
import org.esa.beam.gpf.operators.meris.MerisBasisOp;
import org.esa.beam.meris.l2auxdata.Constants;
import org.esa.beam.util.ProductUtils;

import javax.media.jai.*;
import javax.media.jai.operator.ScaleDescriptor;
import java.awt.*;
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
public class ScapeMSmoothOp extends ScapeMMerisBasisOp implements Constants {

    @SourceProduct(alias = "source")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    private int pixelsPerCell;

    @Override
    public void initialize() throws OperatorException {

        if (sourceProduct.getProductType().contains("_RR")) {
            pixelsPerCell = ScapeMConstants.RR_PIXELS_PER_CELL;
        } else {
            pixelsPerCell = ScapeMConstants.FR_PIXELS_PER_CELL;
        }

        createTargetProduct();
    }

    private void createTargetProduct() throws OperatorException {
        targetProduct = new Product(sourceProduct.getName(),
                                    sourceProduct.getProductType(),
                                    sourceProduct.getSceneRasterWidth(),
                                    sourceProduct.getSceneRasterHeight());
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        targetProduct.setPreferredTileSize(pixelsPerCell, pixelsPerCell);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyTiePointGrids(sourceProduct, targetProduct);
        ProductUtils.copyFlagBands(sourceProduct, targetProduct, true);
        ProductUtils.copyMasks(sourceProduct, targetProduct);

        Band b = sourceProduct.getBand(ScapeMConstants.VISIBILITY_BAND_NAME);
        RenderedImage sourceImage = b.getSourceImage();

        final float upscaleFactor = (float) pixelsPerCell;
        final float downscaleFactor = 1.0f / upscaleFactor;

        ImageLayout targetImageLayout = new ImageLayout();
        targetImageLayout.setTileWidth(sourceImage.getTileWidth());
        targetImageLayout.setTileHeight(sourceImage.getTileHeight());
        final RenderingHints renderingHints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, targetImageLayout);
        renderingHints.put(JAI.KEY_IMAGE_LAYOUT, targetImageLayout);
        renderingHints.put(JAI.KEY_BORDER_EXTENDER, BorderExtender.createInstance(BorderExtender.BORDER_COPY));

        // we come with constant values over a 30x30 km cell, so first downscale to an image with 1 pixel
        // per each of these cells...
        RenderedOp downImage = ScaleDescriptor.create(sourceImage,
                                                      downscaleFactor,
                                                      downscaleFactor,
                                                      0.0f, 0.0f,
                                                      Interpolation.getInstance(
                                                              Interpolation.INTERP_NEAREST), renderingHints);
        // now do the smoothing with upscaling to original size using bicubic interpolation...
        RenderedOp targetImage = ScaleDescriptor.create(downImage,
                                                        upscaleFactor,
                                                        upscaleFactor,
                                                        0.0f, 0.0f,
                                                        Interpolation.getInstance(
                                                                Interpolation.INTERP_BICUBIC), renderingHints);

        Band targetBand = ProductUtils.copyBand(ScapeMConstants.VISIBILITY_BAND_NAME, sourceProduct, targetProduct, false);
        targetBand.setSourceImage(targetImage);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(ScapeMSmoothOp.class);
        }
    }
}
