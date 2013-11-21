package org.esa.beam.operator;


import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.beam.framework.dataop.dem.ElevationModel;
import org.esa.beam.framework.gpf.Tile;
import org.esa.beam.idepix.algorithms.scapem.FubScapeMClassificationOp;
import org.esa.beam.meris.l2auxdata.L2AuxData;
import org.esa.beam.util.math.LookupTable;
import org.esa.beam.util.math.MathUtils;
import org.esa.beam.watermask.operator.WatermaskClassifier;

import java.awt.*;
import java.io.IOException;

/**
 * Class representing SCAPE-M algorithm
 *
 * @author Tonio Fincke, Olaf Danne
 */
public class ScapeMCorrection {


    public ScapeMCorrection(L2AuxData l2AuxData) {
        //To change body of created methods use File | Settings | File Templates.
    }

    /**
     * Determines if cell is regarded as 'clear land' : > 35% must not be water or cloud
     *
     * @param rect
     * @param geoCoding
     * @param cloudFlags
     * @param classifier
     * @return
     */
    boolean isCellClearLand(Rectangle rect, GeoCoding geoCoding, Tile cloudFlags, WatermaskClassifier classifier) {
        int countWater = 0;
        int countCloud2 = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                if (cloudFlags.getSampleBit(x, y, FubScapeMClassificationOp.F_CLOUD_2)) {
                    countCloud2++;
                }

                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    try {
                        if (classifier.isWater(geoPos.lat, geoPos.lon) &&
                                !cloudFlags.getSampleBit(x, y, FubScapeMClassificationOp.F_LAKE)) {
                            countWater++;
                        }
                    } catch (IOException ignore) {
                    }
                }

            }
        }
        return (countCloud2 + countWater) / (rect.getWidth() * rect.getHeight()) <= 0.65;
    }

    /**
     * Returns the elevation mean value over all land pixels in a 30x30km cell
     *
     * @return
     */
    double getHsurfMeanCell(Rectangle rect,
                                   GeoCoding geoCoding,
                                   WatermaskClassifier classifier,
                                   ElevationModel elevationModel) throws Exception {

        double elevMean = 0.0;
        int elevCount = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    try {
                        if (!classifier.isWater(geoPos.lat, geoPos.lon)) {
                            elevMean += elevationModel.getElevation(geoPos);
                            elevCount++;
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
        }

        double hSurf = elevMean/elevCount;
        return hSurf;
    }

    double getCosSzaMeanCell(Rectangle rect,
                            GeoCoding geoCoding,
                            WatermaskClassifier classifier,
                            Tile szaTile) throws Exception {

        double cosSzaMean = 0.0;
        int cosSzaCount = 0;
        for (int y = rect.y; y < rect.y + rect.height; y++) {
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                GeoPos geoPos = null;
                if (geoCoding.canGetGeoPos()) {
                    geoPos = geoCoding.getGeoPos(new PixelPos(x, y), geoPos);
                    try {
                        if (!classifier.isWater(geoPos.lat, geoPos.lon)) {
                            final double sza = szaTile.getSampleDouble(x, y);
                            cosSzaMean += Math.cos(sza* MathUtils.DTOR);
                            cosSzaCount++;
                        }
                    } catch (IOException ignore) {
                    }
                }
            }
        }

        return cosSzaMean/cosSzaCount;
    }
}
