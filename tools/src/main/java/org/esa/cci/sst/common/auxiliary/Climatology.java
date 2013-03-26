/*
 * SST_cci Tools
 *
 * Copyright (C) 2011-2013 by Brockmann Consult GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.esa.cci.sst.common.auxiliary;

import org.esa.cci.sst.common.cellgrid.Downscaling;
import org.esa.cci.sst.common.cellgrid.Grid;
import org.esa.cci.sst.common.cellgrid.GridDef;
import org.esa.cci.sst.common.cellgrid.Unmask;
import org.esa.cci.sst.common.cellgrid.YFlip;
import org.esa.cci.sst.tool.ExitCode;
import org.esa.cci.sst.tool.ToolException;
import org.esa.cci.sst.util.NcUtils;
import ucar.nc2.NetcdfFile;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * OSTIA monthly SST climatology.
 *
 * @author Norman Fomferra
 * @author Ralf Quast
 */
public class Climatology {

    private static final GridDef SOURCE_GRID_DEF = GridDef.createGlobal(0.05);
    private static final GridDef TARGET_1D_GRID_DEF = GridDef.createGlobal(1.0);
    private static final GridDef TARGET_5D_GRID_DEF = GridDef.createGlobal(5.0);
    private static final GridDef TARGET_90D_GRID_DEF = GridDef.createGlobal(90.0);

    private static final Logger LOGGER = Logger.getLogger("org.esa.cci.sst");

    private final File[] dailyClimatologyFiles;
    private final GridDef targetGridDef;

    private Grid sstGrid;
    private int dayOfYear;

    private Grid seaCoverageGrid; //0.1° or 0.5° same as input files
    private Grid seaCoverageCell1Grid;
    private Grid seaCoverageCell5Grid;
    private Grid seaCoverageCell90Grid;

    private Climatology(File[] dailyClimatologyFiles, GridDef targetGridDef) {
        if (dailyClimatologyFiles.length != 365) {
            throw new IllegalArgumentException("files.length != 365");
        }
        this.dailyClimatologyFiles = dailyClimatologyFiles;
        this.targetGridDef = targetGridDef;
    }

    public static Climatology create(File dir, GridDef targetGridDef) throws ToolException {
        if (!dir.isDirectory()) {
            throw new ToolException("Not a directory or directory not found: " + dir, ExitCode.USAGE_ERROR);
        }
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("D") && name.endsWith(".nc.bz2");
            }
        });
        if (files == null || files.length < 365) {
            files = dir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.startsWith("D") && name.endsWith(".nc");
                }
            });
        }
        if (files == null) {
            throw new ToolException("Climatology directory is empty: " + dir, ExitCode.USAGE_ERROR);
        }
        if (files.length == 365) {
            final File[] dailyClimatologyFiles = new File[365];
            for (final File file : files) {
                final int day = Integer.parseInt(file.getName().substring(1, 4));
                dailyClimatologyFiles[day - 1] = file;
            }
            return new Climatology(dailyClimatologyFiles, targetGridDef);
        } else if (files.length == 1) {
            final File[] dailyClimatologyFiles = new File[365];
            for (int i = 0; i < 365; i++) {
                dailyClimatologyFiles[i] = files[0];
            }
            return new Climatology(dailyClimatologyFiles, targetGridDef);
        } else {
            final String[] missingDays = getMissingDays(files);
            throw new ToolException(
                    String.format("Climatology directory is expected to contain 365 files, but found %d. Missing %s.",
                                  files.length, Arrays.toString(missingDays)), ExitCode.USAGE_ERROR);
        }
    }

    public Grid getAnalysedSst(int dayOfYear) throws IOException {
        synchronized (this) {
            if (this.dayOfYear != dayOfYear) {
                readGrids(dayOfYear);
            }
            return sstGrid;
        }
    }

    public Grid getSeaCoverage() {
        return seaCoverageGrid;
    }

    public Grid getSeaCoverageCell1Grid() {
        return seaCoverageCell1Grid;
    }

    public Grid getSeaCoverageCell5Grid() {
        return seaCoverageCell5Grid;
    }

    public Grid getSeaCoverageCell90Grid() {
        return seaCoverageCell90Grid;
    }

    private void readGrids(int dayOfYear) throws IOException {
        if (dayOfYear < 1) {
            throw new IllegalArgumentException("dayOfYear < 1");
        } else if (dayOfYear > 366) {
            throw new IllegalArgumentException("dayOfYear > 366");
        } else if (dayOfYear == 366) {
            dayOfYear = 365; // leap year
        }
        final File file = dailyClimatologyFiles[dayOfYear - 1];
        long t0 = System.currentTimeMillis();
        LOGGER.info(
                String.format("Processing input climatology file '%s' for day of year %d", file.getPath(), dayOfYear));
        final NetcdfFile netcdfFile = NetcdfFile.open("file:" + file.getPath().replace('\\', '/'));
        try {
            readGrids(netcdfFile, dayOfYear);
        } finally {
            netcdfFile.close();
        }
        LOGGER.fine(String.format("Processing input climatology file took %d ms", System.currentTimeMillis() - t0));
    }

    private void readGrids(NetcdfFile netcdfFile, int dayOfYear) throws IOException {
        readAnalysedSstGrid(netcdfFile, dayOfYear);
        if (seaCoverageGrid == null) {
            readSeaCoverageGrids(netcdfFile);
        }
    }

    private void readAnalysedSstGrid(NetcdfFile netcdfFile, int dayOfYear) throws IOException {
        long t0 = System.currentTimeMillis();
        LOGGER.fine("Reading 'analysed_sst'...");
        Grid sstGrid = NcUtils.readGrid(netcdfFile, "analysed_sst", SOURCE_GRID_DEF, 0);
        LOGGER.fine(String.format("Reading 'analysed_sst' took %d ms", System.currentTimeMillis() - t0));
        t0 = System.currentTimeMillis();
        if (!SOURCE_GRID_DEF.equals(targetGridDef)) {
            sstGrid = Downscaling.create(sstGrid, targetGridDef);
        }
        LOGGER.fine(String.format("Transforming 'analysed_sst' took %d ms", System.currentTimeMillis() - t0));
        this.sstGrid = YFlip.create(sstGrid);
        this.dayOfYear = dayOfYear;
    }

    private void readSeaCoverageGrids(NetcdfFile netcdfFile) throws IOException {
        long t0 = System.currentTimeMillis();
        LOGGER.fine("Reading 'mask'...");
        final Grid maskGrid = NcUtils.readGrid(netcdfFile, "mask", SOURCE_GRID_DEF, 0);
        LOGGER.fine(String.format("Reading 'mask' took %d ms", System.currentTimeMillis() - t0));
        t0 = System.currentTimeMillis();
        seaCoverageGrid = YFlip.create(Unmask.create(maskGrid, 0x01));
        if (!SOURCE_GRID_DEF.equals(targetGridDef)) {
            seaCoverageGrid = Downscaling.create(seaCoverageGrid, targetGridDef);
        }
        seaCoverageCell1Grid = Downscaling.create(seaCoverageGrid, TARGET_1D_GRID_DEF);
        seaCoverageCell5Grid = Downscaling.create(seaCoverageGrid, TARGET_5D_GRID_DEF);
        seaCoverageCell90Grid = Downscaling.create(seaCoverageCell5Grid, TARGET_90D_GRID_DEF);
        LOGGER.fine(String.format("Transforming 'mask' took %d ms", System.currentTimeMillis() - t0));
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
}
