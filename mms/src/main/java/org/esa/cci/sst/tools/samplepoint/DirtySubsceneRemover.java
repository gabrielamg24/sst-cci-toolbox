/*
 * Copyright (C) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */

package org.esa.cci.sst.tools.samplepoint;

import org.esa.beam.framework.datamodel.GeoCoding;
import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.PixelPos;
import org.esa.cci.sst.common.ExtractDefinition;
import org.esa.cci.sst.common.ExtractDefinitionBuilder;
import org.esa.cci.sst.data.DataFile;
import org.esa.cci.sst.data.Observation;
import org.esa.cci.sst.orm.Storage;
import org.esa.cci.sst.reader.Reader;
import org.esa.cci.sst.reader.ReaderFactory;
import org.esa.cci.sst.tool.Configuration;
import org.esa.cci.sst.tool.ToolException;
import org.esa.cci.sst.tools.Constants;
import org.esa.cci.sst.util.LocationTest;
import org.esa.cci.sst.util.PixelCounter;
import org.esa.cci.sst.util.SamplingPoint;
import ucar.ma2.Array;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DirtySubsceneRemover {

    private boolean primary;
    private int subSceneWidth;
    private int subSceneHeight;
    private double dirtyPixelFraction;
    private Configuration config;
    private Storage storage;
    private Logger logger;

    public DirtySubsceneRemover() {
        primary = true;
    }

    public DirtySubsceneRemover sensorName() {
        return this;
    }

    public DirtySubsceneRemover primary(boolean primary) {
        this.primary = primary;
        return this;
    }

    public DirtySubsceneRemover subSceneWidth(int subSceneWidth) {
        this.subSceneWidth = subSceneWidth;
        return this;
    }

    public DirtySubsceneRemover subSceneHeight(int subSceneHeight) {
        this.subSceneHeight = subSceneHeight;
        return this;
    }

    public DirtySubsceneRemover dirtyPixelFraction(double dirtyPixelFraction) {
        this.dirtyPixelFraction = dirtyPixelFraction;
        return this;
    }

    public DirtySubsceneRemover config(Configuration config) {
        this.config = config;
        return this;
    }

    public DirtySubsceneRemover storage(Storage storage) {
        this.storage = storage;
        return this;
    }

    public DirtySubsceneRemover logger(Logger logger) {
        this.logger = logger;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void removeSamples(List<SamplingPoint> samples) {
        logInfo("Starting removing dirty samples...");

        final Map<Integer, List<SamplingPoint>> samplesByDatafile = splitByFileId(samples, primary);
        final PixelCounter pixelCounter = new PixelCounter();
        final int maxDirtyPixelCount = (int) Math.floor((subSceneWidth * subSceneHeight) * dirtyPixelFraction);

        final int[] shape = new int[]{1, subSceneHeight, subSceneWidth};
        final ExtractDefinitionBuilder builder = new ExtractDefinitionBuilder().shape(shape);
        final List<SamplingPoint> cleanSamples = new ArrayList<>(samples.size());

        for (final int id : samplesByDatafile.keySet()) {
            final List<SamplingPoint> points = samplesByDatafile.get(id);
            final Observation observation = storage.getObservation(id);
            if (observation == null) {
                continue;
            }

            final DataFile datafile = observation.getDatafile();
            final String datafilePath = datafile.getPath();
            final String archiveRootPath = config.getStringValue(Configuration.KEY_MMS_ARCHIVE_ROOT, ".");
            final File archiveRoot = new File(archiveRootPath);
            final File sourceFile = new File(archiveRoot, datafilePath);
            if (!sourceFile.isFile()) {
                logger.warning("Input file does not exist: " + sourceFile.getAbsolutePath());
                continue;
            }

            try (final Reader reader = ReaderFactory.open(datafile, config)) {
                logInfo(MessageFormat.format("Starting removing dirty samples: data file ''{0}''...", datafilePath));

                final int numCols = reader.getElementCount();
                final int numRows = reader.getScanLineCount();
                final GeoCoding geoCoding = reader.getGeoCoding(0);

                for (final SamplingPoint point : points) {
                    final double lat = point.getLat();
                    final double lon = point.getLon();
                    final LocationTest test = new LocationTest(lon, lat, numCols, numRows, geoCoding).invoke();

                    if (test.isOK()) {
                        final int pixelX = test.getPixelX();
                        final int pixelY = test.getPixelY();
                        final PixelPos pixelPos = test.getPixelPos();
                        final GeoPos geoPos = test.getGeoPos();
                        if (primary) {
                            point.setX(pixelX);
                            point.setY(pixelY);
                            point.setReferenceTime(reader.getTime(0, pixelY));
                            geoCoding.getGeoPos(pixelPos, geoPos);
                            point.setReferenceLat(geoPos.getLat());
                            point.setReferenceLon(geoPos.getLon());
                        } else {
                            point.setReference2Time(reader.getTime(0, pixelY));
                        }

                        final ExtractDefinition extractDefinition = builder.lat(lat).lon(lon).build();
                        final Array maskData = reader.read(Constants.MASK_NAME_MMS_DIRTY, extractDefinition);
                        final int dirtyPixelCount = pixelCounter.count(maskData);
                        if (dirtyPixelCount <= maxDirtyPixelCount) {
                            if (logger != null && logger.isLoggable(Level.FINE)) {
                                final String message = MessageFormat.format(
                                        "Adding sample: found {0}/{1} dirty pixels at ({2}, {3}; {4}, {5}).",
                                        dirtyPixelCount, maxDirtyPixelCount, lon, lat, pixelX, pixelY);
                                logger.info(message);
                            }
                            cleanSamples.add(point);
                        } else {
                            if (logger != null && logger.isLoggable(Level.FINE)) {
                                final String message = MessageFormat.format(
                                        "Removing sample: found {0}/{1} dirty pixels at ({2}, {3}; {4}, {5}).",
                                        dirtyPixelCount, maxDirtyPixelCount, lon, lat, pixelX, pixelY);
                                logger.info(message);
                            }
                        }
                    } else {
                        if (logger != null && logger.isLoggable(Level.FINE)) {
                            final String message = MessageFormat.format(
                                    "Could not find pixel at ({0}, {1}) in datafile ''{2}''.", lon, lat,
                                    datafilePath);
                            logger.fine(message);
                        }
                    }
                }
                logInfo(MessageFormat.format(
                        "Finished removing dirty samples: data file ''{0}'' ({1} clean samples)",
                        datafilePath, cleanSamples.size()));
            } catch (IOException e) {
                throw new ToolException(
                        MessageFormat.format("Cannot read data file ''{0}''.", datafilePath), e,
                        ToolException.TOOL_IO_ERROR);
            }
        }
        samples.clear();
        samples.addAll(cleanSamples);

        logInfo(MessageFormat.format("Finished removing dirty samples: {0} clean samples found in total",
                samples.size()));
    }

    private void logInfo(String message) {
        if (logger != null && logger.isLoggable(Level.INFO)) {
            logger.info(message);
        }
    }

    // package access for testing only tb 2014-03-31
    static Map<Integer, List<SamplingPoint>> splitByFileId(List<SamplingPoint> samples, boolean primary) {
        final Map<Integer, List<SamplingPoint>> samplesByDatafile = new TreeMap<>();
        for (final SamplingPoint point : samples) {
            final int id = primary ? point.getReference() : point.getReference2();
            if (!samplesByDatafile.containsKey(id)) {
                samplesByDatafile.put(id, new ArrayList<SamplingPoint>());
            }
            samplesByDatafile.get(id).add(point);
        }
        return samplesByDatafile;
    }

}
