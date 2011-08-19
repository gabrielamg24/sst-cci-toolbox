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

package org.esa.cci.sst.tools.nwp;

import org.esa.beam.util.math.FracIndex;
import org.esa.cci.sst.util.ProcessRunner;
import org.esa.cci.sst.util.TimeUtil;
import ucar.ma2.Array;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.nc2.Attribute;
import ucar.nc2.Dimension;
import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFileWriteable;
import ucar.nc2.Variable;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

/**
 * NWP extraction tool.
 *
 * @author Ralf Quast
 */
class NwpTool {

    private static final String CDO_NWP_TEMPLATE =
            "#! /bin/sh\n" +
            "${CDO} ${CDO_OPTS} -f nc mergetime ${GGAS_TIMESTEPS} ${GGAS_TIME_SERIES} && " +
            "${CDO} ${CDO_OPTS} -f grb mergetime ${GGAM_TIMESTEPS} ${GGAM_TIME_SERIES} && " +
            "${CDO} ${CDO_OPTS} -f grb mergetime ${SPAM_TIMESTEPS} ${SPAM_TIME_SERIES} && " +
            // attention: chaining the operations below results in a loss of the y dimension in the result file
            "${CDO} ${CDO_OPTS} -f nc -R -t ecmwf setreftime,${REFTIME} -remapbil,${GEO} -selname,Q,O3 ${GGAM_TIME_SERIES} ${GGAM_TIME_SERIES_REMAPPED} && " +
            "${CDO} ${CDO_OPTS} -f nc -t ecmwf setreftime,${REFTIME} -remapbil,${GEO} -sp2gp -selname,LNSP,T ${SPAM_TIME_SERIES} ${SPAM_TIME_SERIES_REMAPPED} && " +
            "${CDO} ${CDO_OPTS} -f nc merge -setreftime,${REFTIME} -remapbil,${GEO} -selname,CI,ASN,SSTK,TCWV,MSL,TCC,U10,V10,T2,D2,AL,SKT ${GGAS_TIME_SERIES} ${GGAM_TIME_SERIES_REMAPPED} ${SPAM_TIME_SERIES_REMAPPED} ${NWP_TIME_SERIES}";

    private static final String CDO_MATCHUP_AN_TEMPLATE =
            "#! /bin/sh\n" +
            "${CDO} ${CDO_OPTS} -f nc mergetime ${GGAS_TIMESTEPS} ${GGAS_TIME_SERIES} && " +
            "${CDO} ${CDO_OPTS} -f nc setreftime,${REFTIME} -remapbil,${GEO} -selname,CI,SSTK,U10,V10 ${GGAS_TIME_SERIES} ${AN_TIME_SERIES}";

    private static final String CDO_MATCHUP_FC_TEMPLATE =
            "#! /bin/sh\n" +
            "${CDO} ${CDO_OPTS} -f nc mergetime ${GAFS_TIMESTEPS} ${GAFS_TIME_SERIES} && " +
            "${CDO} ${CDO_OPTS} -f nc mergetime ${GGFS_TIMESTEPS} ${GGFS_TIME_SERIES} && " +
            // attention: chaining the operations below results in a loss of the y dimension in the result file
            "${CDO} ${CDO_OPTS} -f nc setreftime,${REFTIME} -remapbil,${GEO} -selname,SSTK,MSL,BLH,U10,V10,T2,D2 ${GGFS_TIME_SERIES} ${GGFS_TIME_SERIES_REMAPPED} && " +
            "${CDO} ${CDO_OPTS} -f nc merge -setreftime,${REFTIME} -remapbil,${GEO} -selname,SSHF,SLHF,SSRD,STRD,SSR,STR,EWSS,NSSS,E,TP ${GAFS_TIME_SERIES} ${GGFS_TIME_SERIES_REMAPPED} ${FC_TIME_SERIES}";

    private final boolean matchupRequested;
    private String sensorName;
    private int sensorPattern;

    private String mmdSourceLocation;
    private String nwpSourceLocation;
    private String nwpTargetLocation;
    private String geoFileLocation;
    private String anTargetLocation;
    private String fcTargetLocation;

    private static final int SENSOR_NWP_NX = 1;
    private static final int SENSOR_NWP_NY = 1;
    private static final int SENSOR_NWP_STRIDE_X = 1;

    private static final int SENSOR_NWP_STRIDE_Y = 1;
    private static final int MATCHUP_AN_PAST_TIME_STEP_COUNT = 8;

    private static final int MATCHUP_AN_FUTURE_TIME_STEP_COUNT = 4;
    private static final int MATCHUP_FC_PAST_TIME_STEP_COUNT = 16;
    private static final int MATCHUP_FC_FUTURE_TIME_STEP_COUNT = 8;

    NwpTool(String[] args) {
        if (args.length != 5 && args.length != 6) {
            System.out.println("Usage:");
            System.out.println("\tNwpTool true mmdSourceLocation nwpSourceLocation anTargetLocation fcTargetLocation");
            System.out.println("\tNwpTool false sensorName sensorPattern mmdSourceLocation nwpSourceLocation nwpTargetLocation");
            System.exit(1);
        }
        matchupRequested = Boolean.parseBoolean(args[0]);
        if(matchupRequested) {
            if(args.length != 5) {
                System.out.println("Usage:");
                System.out.println("\tNwpTool true mmdSourceLocation nwpSourceLocation anTargetLocation fcTargetLocation");
                System.out.println("\tNwpTool false sensorName sensorPattern mmdSourceLocation nwpSourceLocation nwpTargetLocation");
                System.exit(1);
            }
            mmdSourceLocation = args[1];
            nwpSourceLocation = args[2];
            anTargetLocation = args[3];
            fcTargetLocation = args[4];
        } else {
            if(args.length != 6) {
                System.out.println("Usage:");
                System.out.println("\tNwpTool true mmdSourceLocation nwpSourceLocation anTargetLocation fcTargetLocation");
                System.out.println("\tNwpTool false sensorName sensorPattern mmdSourceLocation nwpSourceLocation nwpTargetLocation");
                System.exit(1);
            }
            sensorName = args[1];
            sensorPattern = Integer.parseInt(args[2], 16);
            mmdSourceLocation = args[3];
            nwpSourceLocation = args[4];
            nwpTargetLocation = args[5];
        }
    }

    void createMatchupAnFile() throws IOException, InterruptedException {
        final NetcdfFile mmdFile = NetcdfFile.open(mmdSourceLocation);

        final List<String> subDirectories = getNwpSubDirectories(NwpUtil.findVariable(mmdFile, "matchup.time"));

        try {
            writeMatchupGeoFile(mmdFile);

            final Properties properties = new Properties();
            properties.setProperty("CDO", "cdo");
            properties.setProperty("CDO_OPTS", "-M");
            properties.setProperty("REFTIME", "1978-01-01,00:00:00,seconds");

            properties.setProperty("GEO", geoFileLocation);
            properties.setProperty("GGAS_TIMESTEPS", NwpUtil.files(nwpSourceLocation, subDirectories, "ggas[0-9]*.nc"));
            properties.setProperty("GGAS_TIME_SERIES", NwpUtil.createTempFile("ggas", ".nc", true).getPath());
            properties.setProperty("AN_TIME_SERIES", NwpUtil.createTempFile("analysis", ".nc", true).getPath());

            final ProcessRunner runner = new ProcessRunner("org.esa.cci.sst");
            runner.execute(NwpUtil.writeCdoScript(CDO_MATCHUP_AN_TEMPLATE, properties).getPath());

            final NetcdfFile anFile = NetcdfFile.open(properties.getProperty("AN_TIME_SERIES"));
            try {
                NwpUtil.writeAnalysisMmdFile(mmdFile, anFile, anTargetLocation, MATCHUP_AN_PAST_TIME_STEP_COUNT,
                                             MATCHUP_AN_FUTURE_TIME_STEP_COUNT);
            } finally {
                try {
                    anFile.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            try {
                mmdFile.close();
            } catch (IOException ignored) {
            }
        }
    }

    void createMatchupFcFile() throws IOException, InterruptedException {
        final NetcdfFile mmdFile = NetcdfFile.open(mmdSourceLocation);

        final List<String> subDirectories = getNwpSubDirectories(NwpUtil.findVariable(mmdFile, "matchup.time"));

        try {
            writeMatchupGeoFile(mmdFile);

            final Properties properties = new Properties();
            properties.setProperty("CDO", "cdo");
            properties.setProperty("CDO_OPTS", "-M");
            properties.setProperty("REFTIME", "1978-01-01,00:00:00,seconds");

            properties.setProperty("GEO", geoFileLocation);
            properties.setProperty("GAFS_TIMESTEPS", NwpUtil.files(nwpSourceLocation, subDirectories, "gafs[0-9]*.nc"));
            properties.setProperty("GGFS_TIMESTEPS", NwpUtil.files(nwpSourceLocation, subDirectories, "ggfs[0-9]*.nc"));
            properties.setProperty("GAFS_TIME_SERIES", NwpUtil.createTempFile("gafs", ".nc", true).getPath());
            properties.setProperty("GGFS_TIME_SERIES", NwpUtil.createTempFile("ggfs", ".nc", true).getPath());
            properties.setProperty("GGFS_TIME_SERIES_REMAPPED", NwpUtil.createTempFile("ggfr", ".nc", true).getPath());
            properties.setProperty("FC_TIME_SERIES", NwpUtil.createTempFile("forecast", ".nc", true).getPath());

            final ProcessRunner runner = new ProcessRunner("org.esa.cci.sst");
            runner.execute(NwpUtil.writeCdoScript(CDO_MATCHUP_FC_TEMPLATE, properties).getPath());

            final NetcdfFile fcFile = NetcdfFile.open(properties.getProperty("FC_TIME_SERIES"));
            try {
                NwpUtil.writeForecastMmdFile(mmdFile, fcFile, fcTargetLocation, MATCHUP_FC_PAST_TIME_STEP_COUNT,
                                             MATCHUP_FC_FUTURE_TIME_STEP_COUNT);
            } finally {
                try {
                    fcFile.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            try {
                mmdFile.close();
            } catch (IOException ignored) {
            }
        }
    }

    void createMergedFile() throws IOException, InterruptedException {
        final NetcdfFile sensorMmdFile = NetcdfFile.open(writeSensorMmdFile(sensorName, sensorPattern));

        final Variable timeVariable = NwpUtil.findVariable(sensorMmdFile, sensorName + ".time", getAlternativeSensorName(sensorName) + ".time");
        final List<String> subDirectories = getNwpSubDirectories(timeVariable);

        try {
            writeSensorGeoFile(sensorMmdFile, SENSOR_NWP_NX, SENSOR_NWP_NY, SENSOR_NWP_STRIDE_X, SENSOR_NWP_STRIDE_Y);

            final Properties properties = new Properties();
            properties.setProperty("CDO", "cdo");
            properties.setProperty("CDO_OPTS", "-M");
            properties.setProperty("REFTIME", "1978-01-01,00:00:00,seconds");

            properties.setProperty("GEO", geoFileLocation);
            properties.setProperty("GGAS_TIMESTEPS", NwpUtil.files(nwpSourceLocation, subDirectories, "ggas[0-9]*.nc"));
            properties.setProperty("GGAM_TIMESTEPS", NwpUtil.files(nwpSourceLocation, subDirectories, "ggam[0-9]*.grb"));
            properties.setProperty("SPAM_TIMESTEPS", NwpUtil.files(nwpSourceLocation, subDirectories, "spam[0-9]*.grb"));
            properties.setProperty("GGAS_TIME_SERIES", NwpUtil.createTempFile("ggas", ".nc", false).getPath());
            properties.setProperty("GGAM_TIME_SERIES", NwpUtil.createTempFile("ggam", ".nc", false).getPath());
            properties.setProperty("SPAM_TIME_SERIES", NwpUtil.createTempFile("spam", ".nc", false).getPath());
            properties.setProperty("GGAM_TIME_SERIES_REMAPPED", NwpUtil.createTempFile("ggar", ".nc", false).getPath());
            properties.setProperty("SPAM_TIME_SERIES_REMAPPED", NwpUtil.createTempFile("spar", ".nc", false).getPath());
            properties.setProperty("NWP_TIME_SERIES", NwpUtil.createTempFile("nwp", ".nc", false).getPath());

            final ProcessRunner runner = new ProcessRunner("org.esa.cci.sst");
            final String path = NwpUtil.writeCdoScript(CDO_NWP_TEMPLATE, properties).getPath();
            runner.execute(path);

            final NetcdfFile nwpFile = NetcdfFile.open(properties.getProperty("NWP_TIME_SERIES"));
            try {
                writeMmdNwpFile(nwpFile, sensorName);
            } finally {
                try {
                    nwpFile.close();
                } catch (IOException ignored) {
                }
            }
        } finally {
            try {
                sensorMmdFile.close();
            } catch (IOException ignored) {
            }
        }
    }

    private List<String> getNwpSubDirectories(Variable variable) throws IOException {
        final Number fillValue = variable.findAttribute("_FillValue").getNumericValue();
        int startTime = Integer.MAX_VALUE;
        int endTime = Integer.MIN_VALUE;
        final Array times = variable.read();
        for(int i = 0; i < times.getSize(); i++) {
            final int currentTime = times.getInt(i);
            if(currentTime < startTime && currentTime != fillValue.intValue()) {
                startTime = currentTime;
            } else if(currentTime > endTime) {
                endTime = currentTime;
            }
        }
        final Date startDate = TimeUtil.secondsSince1978ToDate(startTime - 60 * 60 * 24 * 3);
        final Date endDate = TimeUtil.secondsSince1978ToDate(endTime + 60 * 60 * 24 * 2);
        final GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.setTime(startDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        final List<String> subDirectories = new ArrayList<String>();
        while (!calendar.getTime().after(endDate)) {
            subDirectories.add(simpleDateFormat.format(calendar.getTime()));
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }
        return subDirectories;
    }

    private void writeMmdNwpFile(NetcdfFile nwpSourceFile, String sensorName) throws IOException {
        final NetcdfFile mmd = NetcdfFile.open(mmdSourceLocation);
        final NetcdfFileWriteable mmdNwp = NetcdfFileWriteable.createNew(nwpTargetLocation, true);

        copySensorVariablesStructure(sensorName, mmd, mmdNwp);
        copyNwpStructure(nwpSourceFile, sensorName, mmd, mmdNwp);

        mmdNwp.create();

        copySensorVariablesData(mmd, mmdNwp);
        copyNwpData(nwpSourceFile, sensorName, mmd, mmdNwp);

        mmdNwp.close();
    }

    private void copyNwpData(NetcdfFile nwpSourceFile, String sensorName, NetcdfFile mmd, NetcdfFileWriteable mmdNwp) throws IOException {
        final Dimension yDimension = NwpUtil.findDimension(nwpSourceFile, "y");
        final Dimension xDimension = NwpUtil.findDimension(nwpSourceFile, "x");

        final int matchupCount = matchupCount(mmd.findVariable(NetcdfFile.escapeName("matchup.sensor_list")).read());
        final int gy = yDimension.getLength() / matchupCount;
        final int gx = xDimension.getLength();

        final Variable targetTimesVariable = NwpUtil.findVariable(mmdNwp, sensorName + ".time", getAlternativeSensorName(sensorName) + ".time");
        final Array matchupIds = NwpUtil.findVariable(mmdNwp, "matchup.id").read();
        final Array sourceTimes = NwpUtil.findVariable(nwpSourceFile, "time").read();
        final Array targetTimes = targetTimesVariable.read();
        final float targetFillValue = NwpUtil.getAttribute(targetTimesVariable, "_FillValue", Short.MIN_VALUE);

        try {
            mmdNwp.write(NetcdfFile.escapeName("matchup.id"), matchupIds);

            for (int i = 0; i < matchupCount; i++) {
                final int[] sourceStart = {0, 0, i * gy, 0};
                final int[] sourceShape = {1, 0, gy, gx};

                final int targetTime = targetTimes.getInt(i);
                if (targetTime == (int) targetFillValue) {
                    continue;
                }

                final FracIndex fi = NwpUtil.interpolationIndex(sourceTimes, targetTime);

                for (final Variable targetVariable : mmdNwp.getVariables()) {
                    if ("matchup.id".equals(targetVariable.getName()) || !targetVariable.getName().contains(".nwp.")) {
                        continue;
                    }
                    final Variable sourceVariable = NwpUtil.findVariable(nwpSourceFile, targetVariable.getName().substring(
                            sensorName.length() + 5));
                    final float fillValue = NwpUtil.getAttribute(sourceVariable, "_FillValue", 2.0E+20F);
                    final float validMin = NwpUtil.getAttribute(sourceVariable, "valid_min", Float.NEGATIVE_INFINITY);
                    final float validMax = NwpUtil.getAttribute(sourceVariable, "valid_max", Float.POSITIVE_INFINITY);

                    sourceStart[0] = fi.i;
                    sourceShape[1] = sourceVariable.getShape(1);

                    final Array slice1 = sourceVariable.read(sourceStart, sourceShape);
                    sourceStart[0] = fi.i + 1;
                    final Array slice2 = sourceVariable.read(sourceStart, sourceShape);
                    for (int k = 0; k < slice1.getSize(); k++) {
                        final float v1 = slice1.getFloat(k);
                        final float v2 = slice2.getFloat(k);
                        final boolean invalid1 = v1 == fillValue || v1 < validMin || v1 > validMax;
                        final boolean invalid2 = v2 == fillValue || v2 < validMin || v2 > validMax;
                        if (invalid1 && invalid2) {
                            slice2.setFloat(k, fillValue);
                        } else if (invalid1) {
                            // do nothing, value is already set
                        } else if (invalid2) {
                            slice2.setFloat(k, v1);
                        } else {
                            slice2.setDouble(k, fi.f * v1 + (1.0 - fi.f) * v2);
                        }
                    }

                    final int[] targetShape = targetVariable.getShape();
                    targetShape[0] = 1;
                    final int[] targetStart = new int[targetShape.length];
                    targetStart[0] = i;
                    mmdNwp.write(targetVariable.getNameEscaped(), targetStart, slice2.reshape(targetShape));
                }
            }
        } catch (InvalidRangeException e) {
            throw new IOException(e);
        } finally {
            try {
                mmdNwp.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void copyNwpStructure(NetcdfFile nwpSourceFile, String sensorName, NetcdfFile mmd, NetcdfFileWriteable mmdNwp) throws IOException {
        final Dimension yDimension = NwpUtil.findDimension(nwpSourceFile, "y");
        final Dimension xDimension = NwpUtil.findDimension(nwpSourceFile, "x");
        final Dimension levDimension = NwpUtil.findDimension(nwpSourceFile, "lev");

        final int matchupCount = matchupCount(mmd.findVariable(NetcdfFile.escapeName("matchup.sensor_list")).read());
        final int gy = yDimension.getLength() / matchupCount;
        final int gx = xDimension.getLength();
        final int gz = levDimension.getLength();

        mmdNwp.addDimension(sensorName + ".nwp.nx", gx);
        mmdNwp.addDimension(sensorName + ".nwp.ny", gy);
        mmdNwp.addDimension(sensorName + ".nwp.nz", gz);

        for (final Variable sourceVariable : nwpSourceFile.getVariables()) {
            if (sourceVariable.getRank() == 4) {
                final Variable targetVariable;
                if (sourceVariable.getDimension(1).getLength() == 1) {
                    targetVariable = mmdNwp.addVariable(
                            sensorName + ".nwp." + sourceVariable.getName(), sourceVariable.getDataType(),
                            String.format("matchup %s.nwp.ny %s.nwp.nx", sensorName, sensorName));
                } else {
                    targetVariable = mmdNwp.addVariable(
                            sensorName + ".nwp." + sourceVariable.getName(), sourceVariable.getDataType(),
                            String.format("matchup %s.nwp.nz %s.nwp.ny %s.nwp.nx", sensorName, sensorName, sensorName));
                }
                for (final Attribute attribute : sourceVariable.getAttributes()) {
                    targetVariable.addAttribute(attribute);
                }
            }
        }
    }

    private void copySensorVariablesData(NetcdfFile mmd, NetcdfFileWriteable mmdNwp) throws IOException {
        final List<Integer> sensorMatchups = new ArrayList<Integer>(10000);
        final Array array = mmd.findVariable(NetcdfFile.escapeName("matchup.sensor_list")).read();
        for (int i = 0; i < array.getSize(); i++) {
            if ((array.getInt(i) & sensorPattern) == sensorPattern) {
                sensorMatchups.add(i);
            }
        }
        try {
            for (Variable targetVariable : mmdNwp.getVariables()) {
                final Variable sourceVariable = mmd.findVariable(targetVariable.getNameEscaped());
                if (sourceVariable == null) {
                    continue;
                }
                int[] sourceOrigin = new int[targetVariable.getRank()];
                for (int i = 0; i < sensorMatchups.size(); i++) {
                    sourceOrigin[0] = sensorMatchups.get(i);
                    final int[] shape = targetVariable.getShape();
                    shape[0] = 1;
                    final Array sourceArray = sourceVariable.read(sourceOrigin, shape);
                    int[] targetOrigin = new int[targetVariable.getRank()];
                    targetOrigin[0] = i;
                    mmdNwp.write(NetcdfFile.escapeName(targetVariable.getName()), targetOrigin, sourceArray);
                }
            }
        } catch (InvalidRangeException e) {
            throw new IOException("Unable to copy variables.", e);
        }
    }

    private void copySensorVariablesStructure(String sensorName, NetcdfFile mmd, NetcdfFileWriteable mmdNwp) throws IOException {
        final int matchupCount = matchupCount(mmd.findVariable(NetcdfFile.escapeName("matchup.sensor_list")).read());
        for (Dimension dimension : mmd.getDimensions()) {
            final String dimensionName = dimension.getName();
            if (dimensionName.startsWith(sensorName.substring(0, sensorName.indexOf('.'))) &&
                !dimensionName.equals("matchup")) {
                mmdNwp.addDimension(null, dimension);
            } else if (dimensionName.equals("matchup")) {
                mmdNwp.addDimension(dimensionName, matchupCount);
            } else if (dimensionName.equals("filename_length")) {
                mmdNwp.addDimension(dimensionName, dimension.getLength());
            }
        }
        for (Variable sourceVariable : mmd.getVariables()) {
            final String variableName = sourceVariable.getName();
            if (variableName.startsWith(sensorName) || variableName.startsWith(getAlternativeSensorName(sensorName)) || variableName.equals("matchup.id")) {
                final Variable targetVariable = mmdNwp.addVariable(variableName, sourceVariable.getDataType(), sourceVariable.getDimensionsString());
                for (Attribute attribute : sourceVariable.getAttributes()) {
                    targetVariable.addAttribute(attribute);
                }
            }
        }
        for (Attribute attribute : mmd.getGlobalAttributes()) {
            mmdNwp.addGlobalAttribute(attribute);
        }
    }

    /**
     * Extracts the records from an MMD file that correspond to a certain sensor.
     *
     * @param sensorName    The sensor name.
     * @param sensorPattern The sensor pattern.
     *
     * @return the location of the netCDF file written.
     *
     * @throws java.io.IOException when an error occurred.
     */
    @SuppressWarnings({"ConstantConditions"})
    private String writeSensorMmdFile(String sensorName, int sensorPattern) throws IOException {
        final NetcdfFile mmd = NetcdfFile.open(mmdSourceLocation);

        try {
            final Dimension matchupDimension = NwpUtil.findDimension(mmd, "matchup");
            final Dimension nyDimension = NwpUtil.findDimension(mmd, sensorName.replaceAll("\\..+", "") + ".ny");
            final Dimension nxDimension = NwpUtil.findDimension(mmd, sensorName.replaceAll("\\..+", "") + ".nx");

            final Array sensorPatterns = NwpUtil.findVariable(mmd, "matchup.sensor_list").read();
            final int matchupCount = matchupCount(sensorPatterns);
            final String sensorMmdLocation = NwpUtil.createTempFile("mmd", ".nc", true).getPath();
            final NetcdfFileWriteable sensorMmd = NetcdfFileWriteable.createNew(sensorMmdLocation, true);

            final int ny = nyDimension.getLength();
            final int nx = nxDimension.getLength();

            sensorMmd.addDimension(matchupDimension.getName(), matchupCount);
            sensorMmd.addDimension(nyDimension.getName(), ny);
            sensorMmd.addDimension(nxDimension.getName(), nx);

            NwpUtil.addVariable(sensorMmd, NwpUtil.findVariable(mmd, "matchup.id"));
            NwpUtil.addVariable(sensorMmd, NwpUtil.findVariable(mmd, sensorName + ".latitude", getAlternativeSensorName(sensorName) + ".latitude"));
            NwpUtil.addVariable(sensorMmd, NwpUtil.findVariable(mmd, sensorName + ".longitude", getAlternativeSensorName(sensorName) + ".longitude"));
            NwpUtil.addVariable(sensorMmd, NwpUtil.findVariable(mmd, sensorName + ".time", getAlternativeSensorName(sensorName) + ".time"));

            sensorMmd.create();

            try {
                for (final Variable v : mmd.getVariables()) {
                    final int[] sourceStart = new int[v.getRank()];
                    final int[] sourceShape = v.getShape();
                    final int[] targetStart = new int[v.getRank()];
                    if (sensorMmd.findVariable(v.getNameEscaped()) != null) {
                        for (int m = 0, n = 0; m < matchupDimension.getLength(); m++) {
                            if ((sensorPatterns.getInt(m) & sensorPattern) == sensorPattern) {
                                sourceStart[0] = m;
                                sourceShape[0] = 1;
                                targetStart[0] = n;
                                final Array data = v.read(sourceStart, sourceShape);
                                sensorMmd.write(v.getNameEscaped(), targetStart, data);
                                n++;
                            }
                        }
                    }
                }
            } catch (InvalidRangeException e) {
                throw new IOException(e);
            } finally {
                try {
                    sensorMmd.close();
                } catch (IOException ignored) {
                }
            }
            return sensorMmd.getLocation();
        } finally {
            try {
                mmd.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * Writes the match-up geo-coordinates from an MMD file to a SCRIP compatible file.
     *
     * @param mmd The MMD file.
     *
     * @return the location of netCDF file written.
     *
     * @throws java.io.IOException when an error occurred.
     */
    @SuppressWarnings({"ConstantConditions"})
    private void writeMatchupGeoFile(NetcdfFile mmd) throws IOException {
        final Dimension matchupDimension = NwpUtil.findDimension(mmd, "matchup");

        final String location = NwpUtil.createTempFile("geo", ".nc", true).getPath();
        final NetcdfFileWriteable geoFile = NetcdfFileWriteable.createNew(location, true);

        final int matchupCount = matchupDimension.getLength();

        geoFile.addDimension("grid_size", matchupCount);
        geoFile.addDimension("grid_matchup", matchupCount);
        geoFile.addDimension("grid_ny", 1);
        geoFile.addDimension("grid_nx", 1);
        geoFile.addDimension("grid_corners", 4);
        geoFile.addDimension("grid_rank", 2);

        geoFile.addVariable("grid_dims", DataType.INT, "grid_rank");
        geoFile.addVariable("grid_center_lat", DataType.FLOAT, "grid_size").addAttribute(
                new Attribute("units", "degrees"));
        geoFile.addVariable("grid_center_lon", DataType.FLOAT, "grid_size").addAttribute(
                new Attribute("units", "degrees"));
        geoFile.addVariable("grid_imask", DataType.INT, "grid_size");
        geoFile.addVariable("grid_corner_lat", DataType.FLOAT, "grid_size grid_corners");
        geoFile.addVariable("grid_corner_lon", DataType.FLOAT, "grid_size grid_corners");

        geoFile.addGlobalAttribute("title", "MMD geo-location in SCRIP format");

        geoFile.create();

        try {
            geoFile.write("grid_dims", Array.factory(new int[]{1, matchupCount}));

            final int[] sourceStart = {0};
            final int[] sourceShape = {1};
            final int[] sourceStride = {1};
            final int[] targetStart = {0};
            final int[] targetShape = {1};
            final Array maskData = Array.factory(DataType.INT, targetShape);

            final Variable sourceLat = NwpUtil.findVariable(mmd, "matchup.latitude");
            final Variable sourceLon = NwpUtil.findVariable(mmd, "matchup.longitude");

            for (int i = 0; i < matchupCount; i++) {
                sourceStart[0] = i;
                targetStart[0] = i;
                final Section sourceSection = new Section(sourceStart, sourceShape, sourceStride);
                final Array latData = sourceLat.read(sourceSection);
                final Array lonData = sourceLon.read(sourceSection);
                for (int k = 0; k < targetShape[0]; k++) {
                    final float lat = latData.getFloat(k);
                    final float lon = lonData.getFloat(k);
                    maskData.setInt(k, lat >= -90.0f && lat <= 90.0f && lon >= -180.0f && lat <= 180.0f ? 1 : 0);
                }
                geoFile.write("grid_center_lat", targetStart, latData.reshape(targetShape));
                geoFile.write("grid_center_lon", targetStart, lonData.reshape(targetShape));
                geoFile.write("grid_imask", targetStart, maskData);
            }
        } catch (InvalidRangeException e) {
            throw new IOException(e);
        } finally {
            try {
                geoFile.close();
            } catch (IOException ignored) {
            }
        }
        geoFileLocation = geoFile.getLocation();
    }

    /**
     * Writes the sensor geo-coordinates from an MMD file to a SCRIP compatible file.
     *
     * @param mmd     The MMD file.
     * @param gx      The the number of tie points in x direction.
     * @param gy      The the number of tie points in y direction.
     * @param strideX The tie point stride in x direction.
     * @param strideY The tie point stride in y direction.
     *
     * @return the location of netCDF file written.
     *
     * @throws java.io.IOException when an error occurred.
     */
    @SuppressWarnings({"ConstantConditions"})
    private void writeSensorGeoFile(NetcdfFile mmd, int gx, int gy, int strideX, int strideY) throws IOException {
        final Dimension matchupDimension = NwpUtil.findDimension(mmd, "matchup");
        final Dimension nyDimension = NwpUtil.findDimension(mmd, sensorName.replaceAll("\\..+", "") + ".ny");
        final Dimension nxDimension = NwpUtil.findDimension(mmd, sensorName.replaceAll("\\..+", "") + ".nx");

        final String location = NwpUtil.createTempFile("geo", ".nc", true).getPath();
        final NetcdfFileWriteable geoFile = NetcdfFileWriteable.createNew(location, true);

        final int matchupCount = matchupDimension.getLength();
        final int ny = nyDimension.getLength();
        final int nx = nxDimension.getLength();

        geoFile.addDimension("grid_size", matchupCount * gy * gx);
        geoFile.addDimension("grid_matchup", matchupCount);
        geoFile.addDimension("grid_ny", gy);
        geoFile.addDimension("grid_nx", gx);
        geoFile.addDimension("grid_corners", 4);
        geoFile.addDimension("grid_rank", 2);

        geoFile.addVariable("grid_dims", DataType.INT, "grid_rank");
        geoFile.addVariable("grid_center_lat", DataType.FLOAT, "grid_size").addAttribute(new Attribute("units", "degrees"));
        geoFile.addVariable("grid_center_lon", DataType.FLOAT, "grid_size").addAttribute(new Attribute("units", "degrees"));
        geoFile.addVariable("grid_imask", DataType.INT, "grid_size");
        geoFile.addVariable("grid_corner_lat", DataType.FLOAT, "grid_size grid_corners");
        geoFile.addVariable("grid_corner_lon", DataType.FLOAT, "grid_size grid_corners");

        geoFile.addGlobalAttribute("title", "MMD geo-location in SCRIP format");

        geoFile.create();

        try {
            geoFile.write("grid_dims", Array.factory(new int[]{gx, gy * matchupCount}));

            final int[] sourceStart = {0, (ny >> 1) - (gy >> 1) * strideY, (nx >> 1) - (gx >> 1) * strideX};
            final int[] sourceShape = {1, gy * strideY, gx * strideX};
            final int[] sourceStride = {1, strideY, strideX};
            final int[] targetStart = {0};
            final int[] targetShape = {gy * gx};
            final Array maskData = Array.factory(DataType.INT, targetShape);

            final Variable sourceLat = NwpUtil.findVariable(mmd, sensorName + ".latitude", getAlternativeSensorName(sensorName) + ".latitude");
            final Variable sourceLon = NwpUtil.findVariable(mmd, sensorName + ".longitude", getAlternativeSensorName(sensorName) + ".longitude");

            for (int i = 0; i < matchupCount; i++) {
                sourceStart[0] = i;
                targetStart[0] = i * gy * gx;
                final Section sourceSection = new Section(sourceStart, sourceShape, sourceStride);
                final Array latData = sourceLat.read(sourceSection);
                final Array lonData = sourceLon.read(sourceSection);
                for (int k = 0; k < targetShape[0]; k++) {
                    final float lat = latData.getFloat(k);
                    final float lon = lonData.getFloat(k);
                    maskData.setInt(k, lat >= -90.0f && lat <= 90.0f && lon >= -180.0f && lat <= 180.0f ? 1 : 0);
                }
                geoFile.write("grid_center_lat", targetStart, latData.reshape(targetShape));
                geoFile.write("grid_center_lon", targetStart, lonData.reshape(targetShape));
                geoFile.write("grid_imask", targetStart, maskData);
            }
        } catch (InvalidRangeException e) {
            throw new IOException(e);
        } finally {
            try {
                geoFile.close();
            } catch (IOException ignored) {
            }
        }
        geoFileLocation = geoFile.getLocation();
    }

    static String getAlternativeSensorName(String sensorName) {
        if(!sensorName.startsWith("avhrr.")) {
            return sensorName;
        }
        return new StringBuilder(sensorName.substring(0, sensorName.indexOf(".")))
                .append(".")
                .append(sensorName.substring(sensorName.indexOf(".") + 2)).toString();
    }

    private int matchupCount(Array sensorPatterns) {
        int matchupCount = 0;
        for (int i = 0; i < sensorPatterns.getSize(); ++i) {
//            if ((sensorPatterns.getInt(i) & sensorPattern) != 0) {
            if ((sensorPatterns.getInt(i) & sensorPattern) == sensorPattern) {
                matchupCount++;
            }
        }
        return matchupCount;
    }

    boolean computeMatchups() {
        return matchupRequested;
    }
}
