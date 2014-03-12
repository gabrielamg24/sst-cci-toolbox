/*
 * Copyright (C) 2011 Brockmann Consult GmbH (info@brockmann-consult.de)
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

package org.esa.cci.sst.tools.mmdgeneration;

import org.esa.cci.sst.tools.BasicTool;
import org.esa.cci.sst.tools.Constants;
import org.esa.cci.sst.tools.ToolException;
import ucar.ma2.Array;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriter;
import ucar.nc2.Variable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Re-writes given variables to given MMD file.
 *
 * @author Thomas Storm
 */
public class MmdUpdater extends BasicTool {

    final List<Variable> variables = new ArrayList<Variable>(5);
    private MmdWriter mmdWriter;

    protected MmdUpdater() {
        super("mmdupdate-tool.sh", "0.1");
    }

    public static void main(String[] args) {
        final MmdUpdater mmdUpdater = new MmdUpdater();
        final boolean performWork = mmdUpdater.setCommandLineArgs(args);
        if (!performWork) {
            return;
        }
        mmdUpdater.initialize();
        mmdUpdater.run(args);
        mmdUpdater.close();
    }

    @Override
    public void initialize() {
        super.initialize();
        openMmd();
        parseVariables();
    }

    private void run(String[] args) {
        final MmdTool mmdTool = new MmdTool();
        mmdTool.setCommandLineArgs(args);
        mmdTool.initialize();
        final String mmdLocation = getConfig().getStringValue("mms.mmdupdate.mmd");
        final Map<Integer, Integer> recordOfMatchupMap = createInvertedIndexOfMatchups(mmdLocation, null);
        mmdTool.writeMmdShuffled(mmdWriter, recordOfMatchupMap);
    }

    private Map<Integer, Integer> createInvertedIndexOfMatchups(String path, File archiveRoot) {
        try {
            final String fileLocation;
            if (archiveRoot == null || path.startsWith(File.separator)) {
                fileLocation = path;
            } else {
                fileLocation = archiveRoot.getPath() + File.separator + path;
            }
            //validateFileLocation(fileLocation);
            NetcdfFile ncFile = NetcdfFile.open(fileLocation);
            Variable matchupIds = ncFile.findVariable(NetcdfFile.makeValidPathName(Constants.VARIABLE_NAME_MATCHUP_ID));
            // allow for matchup_id instead of matchup.id to support ARC2 output
            if (matchupIds == null) {
                matchupIds = ncFile.findVariable(NetcdfFile.makeValidPathName(Constants.VARIABLE_NAME_ARC2_MATCHUP_ID));
            }
            int noOfRecords = matchupIds.getShape()[0];
            final Array matchupId = matchupIds.read(new int[]{0}, matchupIds.getShape());
            final HashMap<Integer, Integer> recordOfMatchupMap = new HashMap<Integer, Integer>();
            for (int recordNo = 0; recordNo < noOfRecords; ++recordNo) {
                int matchupIdx = matchupId.getInt(recordNo);
                recordOfMatchupMap.put(matchupIdx, recordNo);
            }
            ncFile.close();
            return recordOfMatchupMap;
        } catch (Exception e) {
            throw new ToolException("error reading matchup index from existing mmd " + path + ": " + e.getMessage(), e, ToolException.TOOL_ERROR);
        }
    }

    private void close() {
        try {
            mmdWriter.close();
        } catch (IOException e) {
            getLogger().warning("File could not be closed: " + e.getMessage());
        }
    }

    void openMmd() {
        final String mmdLocation = getConfig().getStringValue("mms.mmdupdate.mmd");
        try {
            final boolean canOpen = MmdWriter.canOpen(mmdLocation);
            if (!canOpen) {
                throw new ToolException("Cannot open file '" + mmdLocation + "'.", ToolException.TOOL_IO_ERROR);
            }
            mmdWriter = MmdWriter.open(mmdLocation);
        } catch (Exception e) {
            throw new ToolException(e.getMessage(), e, ToolException.TOOL_CONFIGURATION_ERROR);
        }
    }

    void parseVariables() {
        final String updateVariables = getConfig().getStringValue("mms.mmdupdate.variables");
        final NetcdfFileWriter fileWriter = mmdWriter.getFileWriter();
        for (String updateVariable : updateVariables.split(",")) {
            // @todo 1 tb/tb move to MmdWriter class tb 2014-03-12
            final Variable variable = fileWriter.findVariable(NetcdfFile.makeValidPathName(updateVariable));
            if (variable == null) {
                getLogger().warning("Variable '" + updateVariable + "' not found in mmd file.");
            }
            variables.add(variable);
        }
    }
}
