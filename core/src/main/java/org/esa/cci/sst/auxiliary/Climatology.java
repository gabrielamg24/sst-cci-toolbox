/*
 * Copyright (c) 2014 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.cci.sst.auxiliary;

import org.esa.cci.sst.grid.*;
import org.esa.cci.sst.log.SstLogging;
import org.esa.cci.sst.netcdf.NcTools;
import org.esa.cci.sst.tool.ToolException;
import org.esa.cci.sst.util.StopWatch;
import ucar.nc2.NetcdfFile;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OSTIA monthly SST climatology.
 *
 * @author Norman Fomferra
 * @author Ralf Quast
 */
public class Climatology {

    private static final GridDef SOURCE_GRID_DEF = GridDef.createGlobal(0.05);
    private static final GridDef TARGET_5D_GRID_DEF = GridDef.createGlobal(5.0);
    private static final GridDef TARGET_90D_GRID_DEF = GridDef.createGlobal(90.0);

    private static final Logger logger = SstLogging.getLogger();

    private final File[] dailyClimatologyFiles;
    private final GridDef targetGridDef;

    private Grid sstGrid;
    private int dayOfYear;

    private Grid seaCoverageGrid; // 0.1° or 0.05° same as input files
    private Grid seaCoverageCell5Grid;
    private Grid seaCoverageCell90Grid;

    public static Climatology create(File dir, GridDef targetGridDef) throws ToolException {
        if (!dir.isDirectory()) {
            throw new ToolException("Not a directory or directory not found: " + dir, ToolException.TOOL_USAGE_ERROR);
        }
        final File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.matches("D\\d\\d\\d.*\\.nc");
            }
        });
        if (files == null) {
            throw new ToolException(String.format("Climatology directory is empty: %s", dir), ToolException.TOOL_USAGE_ERROR);
        }
        if (files.length == 365 || files.length == 366) {
            final File[] dailyClimatologyFiles = new File[files.length];
            final Pattern pattern = Pattern.compile("\\d\\d\\d");
            for (final File file : files) {
                final Matcher matcher = pattern.matcher(file.getName());
                if (matcher.find()) {
                    final int day = Integer.parseInt(file.getName().substring(matcher.start(), matcher.end()));
                    dailyClimatologyFiles[day - 1] = file;
                } else {
                    throw new ToolException("An internal error occurred.", ToolException.TOOL_INTERNAL_ERROR);
                }
            }
            return new Climatology(dailyClimatologyFiles, targetGridDef);
        } else if (files.length == 1) {
            final File[] dailyClimatologyFiles = new File[]{files[0]};
            return new Climatology(dailyClimatologyFiles, targetGridDef);
        } else {
            final String[] missingDays = getMissingDays(files);
            final String message = String.format("Climatology directory is expected to contain 365 or 366 files, but found %d. Missing %s.",
                    files.length,
                    Arrays.toString(missingDays));
            throw new ToolException(message, ToolException.TOOL_USAGE_ERROR);
        }
    }

    public Grid getSstGrid(int dayOfYear) throws IOException {
        synchronized (this) {
            if (this.dayOfYear != dayOfYear) {
                readGrids(dayOfYear);
            }
            return sstGrid;
        }
    }

    public Grid getSeaCoverageGrid() {
        return seaCoverageGrid;
    }

    public Grid getSeaCoverageGrid5() {
        return seaCoverageCell5Grid;
    }

    public Grid getSeaCoverageGrid90() {
        return seaCoverageCell90Grid;
    }

    private void readGrids(int doy) throws IOException {
        if (doy < 1) {
            throw new IllegalArgumentException("dayOfYear < 1");
        } else if (doy > 366) {
            throw new IllegalArgumentException("dayOfYear > 366");
        }
        if (doy > dailyClimatologyFiles.length) {
            doy = dailyClimatologyFiles.length;
        }
        final File file = dailyClimatologyFiles[doy - 1];

        final StopWatch stopWatch = new StopWatch();
        logger.info(String.format("Processing input climatology file '%s' for day of year %d", file.getPath(), doy));
        stopWatch.start();

        final NetcdfFile netcdfFile = NetcdfFile.open("file:" + file.getPath().replace('\\', '/'));
        try {
            readGrids(netcdfFile, doy);
        } finally {
            netcdfFile.close();
        }

        stopWatch.stop();
        logger.fine(String.format("Processing input climatology file took %d ms", stopWatch.getElapsedMillis()));
    }

    private void readGrids(NetcdfFile netcdfFile, int dayOfYear) throws IOException {
        readAnalysedSstGrid(netcdfFile, dayOfYear);
        if (seaCoverageGrid == null) {
            readSeaCoverageGrids(netcdfFile);
            // for debugging only
            // writeMaskImage();
        }
    }

    private void readAnalysedSstGrid(NetcdfFile netcdfFile, int dayOfYear) throws IOException {
        final StopWatch stopWatch = new StopWatch();

        logger.fine("Reading 'analysed_sst'...");
        stopWatch.start();

        Grid sstGrid = NcTools.readGrid(netcdfFile, "analysed_sst", SOURCE_GRID_DEF, 0);

        stopWatch.stop();
        logger.fine(String.format("Reading 'analysed_sst' took %d ms", stopWatch.getElapsedMillis()));

        stopWatch.start();
        if (!SOURCE_GRID_DEF.equals(targetGridDef)) {
            sstGrid = Downscaling.create(sstGrid, targetGridDef);
        }

        stopWatch.stop();
        logger.fine(String.format("Transforming 'analysed_sst' took %d ms", stopWatch.getElapsedMillis()));

        this.sstGrid = YFlip.create(sstGrid);
        this.dayOfYear = dayOfYear;
    }

    private void readSeaCoverageGrids(NetcdfFile netcdfFile) throws IOException {
        final StopWatch stopWatch = new StopWatch();

        logger.fine("Reading 'mask'...");
        stopWatch.start();

        final Grid maskGrid = NcTools.readGrid(netcdfFile, "mask", SOURCE_GRID_DEF, 0);

        stopWatch.stop();
        logger.fine(String.format("Reading 'mask' took %d ms", stopWatch.getElapsedMillis()));

        stopWatch.start();
        seaCoverageGrid = YFlip.create(Mask.create(maskGrid, 0x01));
        if (!SOURCE_GRID_DEF.equals(targetGridDef)) {
            seaCoverageGrid = Downscaling.create(seaCoverageGrid, targetGridDef);
        }
        seaCoverageCell5Grid = Downscaling.create(seaCoverageGrid, TARGET_5D_GRID_DEF);
        seaCoverageCell90Grid = Downscaling.create(seaCoverageCell5Grid, TARGET_90D_GRID_DEF);

        stopWatch.stop();
        logger.fine(String.format("Transforming 'mask' took %d ms", stopWatch.getElapsedMillis()));
    }

    private static String[] getMissingDays(File[] files) {
        final Set<String> missing = new HashSet<String>();
        for (int i = 0; i < 365; i++) {
            missing.add(String.format("D%03d", i + 1));
        }
        for (File file : files) {
            missing.remove(file.getName().substring(0, 4));
        }
        final String[] strings = missing.toArray(new String[missing.size()]);
        Arrays.sort(strings);
        return strings;
    }

    // for debugging only (don't delete)
//    private void writeMaskImage() throws IOException {
//        final IndexColorModel colorModel = new IndexColorModel(8, 2, new byte[]{0, (byte) 255},
//                new byte[]{0, (byte) 255}, new byte[]{0, (byte) 255});
//        final int w = seaCoverageGrid.getGridDef().getWidth();
//        final int h = seaCoverageGrid.getGridDef().getHeight();
//        final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
//        final WritableRaster imageRaster = image.getRaster();
//        for (int y = 0; y < h; y++) {
//            for (int x = 0; x < w; x++) {
//                imageRaster.setSample(x, y, 0, seaCoverageGrid.getSampleInt(x, y));
//            }
//        }
//        ImageIO.write(image, "PNG", new File("sea-coverage-grid.png"));
//    }

    private Climatology(File[] dailyClimatologyFiles, GridDef targetGridDef) {
        this.dailyClimatologyFiles = dailyClimatologyFiles;
        this.targetGridDef = targetGridDef;
    }
}
