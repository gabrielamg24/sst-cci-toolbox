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

package org.esa.cci.sst.tools;

import com.bc.ceres.core.Assert;
import org.esa.cci.sst.data.Coincidence;
import org.esa.cci.sst.data.GlobalObservation;
import org.esa.cci.sst.data.InsituObservation;
import org.esa.cci.sst.data.Matchup;
import org.esa.cci.sst.data.Observation;
import org.esa.cci.sst.data.ReferenceObservation;
import org.esa.cci.sst.data.RelatedObservation;
import org.esa.cci.sst.data.Sensor;
import org.esa.cci.sst.data.Timeable;
import org.esa.cci.sst.util.TimeUtil;

import javax.persistence.Query;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Tool to compute multi-sensor match-ups from the MMS database.
 */
public class MatchupTool extends BasicTool {

    private static final String SENSOR_OBSERVATION_QUERY =
            "select o"
                    + " from ReferenceObservation o"
                    + " where o.sensor = ?1"
                    + " and o.time >= ?2 and o.time < ?3"
                    + " order by o.time, o.id";

    private static final String SINGLE_SENSOR_OBSERVATION_QUERY =
            "select o"
                    + " from ReferenceObservation o"
                    + " where o.sensor = ?1"
                    + " and o.time >= ?2 and o.time < ?3"
                    + " and not exists (select m from Matchup m"
                    + "                 where m.refObs = o)"
                    + " and not exists (select c from Coincidence c"
                    + "                 where c.observation = o)"
                    + " order by o.time, o.id";

    private static final String MATCHUPS_QUERY =
            "select m"
                    + " from Matchup m inner join m.refObs o"
                    + " where o.time >= ?1 and o.time < ?2"
                    + " order by o.time, m.id";

    private static final String SECONDARY_OBSERVATION_QUERY =
            "select o from ReferenceObservation o"
                    + " where o.sensor = ?1"
                    + " and o.time >= ?2 and o.time < ?3"
                    + " and not exists (select c from Coincidence c"
                    + "                 where c.observation = o)"
                    + " order by o.time, o.id";

    private static final String COINCIDING_OBSERVATION_QUERY =
            "select o.id"
                    + " from mm_observation o, mm_observation oref"
                    + " where oref.id = ?"
                    + " and o.sensor = ?"
                    + " and o.time >= oref.time - interval '12:00:00' and o.time < oref.time + interval '12:00:00'"
                    + " and st_intersects(o.location, oref.point)"
                    + " order by abs(extract(epoch from o.time) - extract(epoch from oref.time))";

    private static final String COINCIDING_GLOBALOBS_QUERY =
            "select o.id"
                    + " from mm_observation o, mm_observation oref"
                    + " where oref.id = ?"
                    + " and o.sensor = ?"
                    + " and o.time >= oref.time - interval '12:00:00' and o.time < oref.time + interval '12:00:00'"
                    + " order by abs(extract(epoch from o.time) - extract(epoch from oref.time))";

    private static final String COINCIDING_INSITUOBS_QUERY =
            "select o.id"
                    + " from mm_observation o, mm_observation oref"
                    + " where oref.id = ?"
                    + " and o.sensor = ?"
                    + " and o.name = oref.name"
                    + " and abs(extract(epoch from o.time) - extract(epoch from oref.time)) <= o.timeRadius"
                    + " order by abs(extract(epoch from o.time) - extract(epoch from oref.time))";


    private static final int CHUNK_SIZE = 1024*16;

    private static final String ATSR_MD = "atsr_md";
    private static final String METOP = "metop";

    private static final String SEVIRI = "seviri";
    private static final Map<Class<? extends Observation>, String> OBSERVATION_QUERY_MAP =
            new HashMap<Class<? extends Observation>, String>(12);

    private Sensor atsrSensor;
    private Sensor metopSensor;
    private Sensor seviriSensor;

    static {
        OBSERVATION_QUERY_MAP.put(ReferenceObservation.class, COINCIDING_OBSERVATION_QUERY);
        OBSERVATION_QUERY_MAP.put(RelatedObservation.class, COINCIDING_OBSERVATION_QUERY);
        OBSERVATION_QUERY_MAP.put(GlobalObservation.class, COINCIDING_GLOBALOBS_QUERY);
        OBSERVATION_QUERY_MAP.put(InsituObservation.class, COINCIDING_INSITUOBS_QUERY);
    }

    public static void main(String[] args) {
        final MatchupTool tool = new MatchupTool();
        try {
            if (!tool.setCommandLineArgs(args)) {
                return;
            }
            tool.initialize();
            tool.run();
        } catch (ToolException e) {
            tool.getErrorHandler().terminate(e);
        } catch (Exception e) {
            tool.getErrorHandler().terminate(new ToolException(e.getMessage(), e, ToolException.UNKNOWN_ERROR));
        }
    }

    private MatchupTool() {
        super("mmsmatchup.sh", "0.1");
    }

    @Override
    public void initialize() {
        super.initialize();
        atsrSensor = getSensor(ATSR_MD);
        metopSensor = getSensor(METOP);
        seviriSensor = getSensor(SEVIRI);
    }

    private void run() {
        if (Boolean.parseBoolean(getConfiguration().getProperty("mms.matchup.cleanup"))) {
            cleanup();
        }
        if (Boolean.parseBoolean(getConfiguration().getProperty("mms.matchup.atsr_md"))) {
            findAtsrMultiSensorMatchups();
        }
        if (Boolean.parseBoolean(getConfiguration().getProperty("mms.matchup.metop"))) {
            findMetopMultiSensorMatchups();
        }
        if (configurationIndexOf("atsr_md") != -1) {
            findSingleSensorMatchups(ATSR_MD, atsrSensor);
        }
        if (configurationIndexOf("metop") != -1) {
            findSingleSensorMatchups(METOP, metopSensor);
        }
        if (configurationIndexOf("seviri") != -1) {
            findSingleSensorMatchups(SEVIRI, seviriSensor);
        }
        findRelatedObservations();
    }

    /**
     * Loops over (A)ATSR reference observations and inquires METOP and SEVIRI
     * fulfilling the coincidence criterion by a spatio-temporal database query.
     * Creates matchups for the temporally nearest coincidences. Does the same
     * for METOP as reference and SEVIRI as inquired coincidence.
     */
    private void findAtsrMultiSensorMatchups() {
        try {
            getPersistenceManager().transaction();

            int count = 0;
            long time = System.currentTimeMillis();

            final Query query = createIncrementalQuery(ATSR_MD, SENSOR_OBSERVATION_QUERY);
            for (int cursor = 0; ; ) {
                final List<ReferenceObservation> atsrObservations = query.setFirstResult(cursor).getResultList();
                if (atsrObservations.size() == 0) {
                    break;
                }
                cursor += atsrObservations.size();

                for (final ReferenceObservation atsrObservation : atsrObservations) {
                    Matchup matchup = null;

                    // determine corresponding metop observation if any
                    if (metopSensor != null) {
                        final Observation metopObservation = findCoincidingObservation(atsrObservation, metopSensor);
                        if (metopObservation != null) {
                            matchup = createMatchup(atsrObservation, atsrSensor.getPattern() | metopSensor.getPattern());
                            createCoincidence(matchup, metopObservation);
                        }
                    }

                    // determine corresponding seviri observation if any
                    if (seviriSensor != null) {
                        final Observation seviriObservation = findCoincidingObservation(atsrObservation, seviriSensor);
                        if (seviriObservation != null) {
                            if (matchup == null) {
                                matchup = createMatchup(atsrObservation, atsrSensor.getPattern() | seviriSensor.getPattern());
                            } else {
                                matchup.setPattern(matchup.getPattern() | seviriSensor.getPattern());
                            }
                            createCoincidence(matchup, seviriObservation);
                        }
                    }

                    ++count;
                    if (count % 1024 == 0) {
                        getPersistenceManager().commit();
                        getPersistenceManager().transaction();
                        getLogger().info(MessageFormat.format("{0} {1} processed in {2} ms.",
                                                              count,
                                                              atsrSensor.getName(),
                                                              System.currentTimeMillis() - time));
                        time = System.currentTimeMillis();
                    }
                }
            }

            getPersistenceManager().commit();
        } catch (Exception e) {
            getPersistenceManager().rollback();
            throw new ToolException(e.getMessage(), e, ToolException.TOOL_ERROR);
        }
    }

    /**
     * Loops over METOP reference observations and inquires SEVIRI
     * fulfilling the coincidence criterion by a spatio-temporal database query.
     * Creates matchups for the temporally nearest coincidences.
     */
    private void findMetopMultiSensorMatchups() {
        if (seviriSensor == null) {
            return;
        }
        try {
            getPersistenceManager().transaction();

            int count = 0;
            long time = System.currentTimeMillis();
            final Query query = createIncrementalQuery(METOP, SECONDARY_OBSERVATION_QUERY);
            for (int cursor = 0; ;) {
                final List<ReferenceObservation> metopObservations = query.setFirstResult(cursor).getResultList();
                if (metopObservations.size() == 0) {
                    break;
                }
                cursor += metopObservations.size();

                for (final ReferenceObservation metopObservation : metopObservations) {
                    // determine corresponding seviri observation if any
                    if (seviriSensor != null) {
                        final Observation seviriObservation = findCoincidingObservation(metopObservation, seviriSensor);
                        if (seviriObservation != null) {
                            final Matchup matchup = createMatchup(metopObservation, metopSensor.getPattern() | seviriSensor.getPattern());
                            createCoincidence(matchup, seviriObservation);
                        }
                    }
                    ++count;
                    if (count % 1024 == 0) {
                        getPersistenceManager().commit();
                        getPersistenceManager().transaction();
                        getLogger().info(MessageFormat.format("{0} {1} processed in {2} ms.",
                                                              count,
                                                              metopSensor.getName(),
                                                              System.currentTimeMillis() - time));
                        time = System.currentTimeMillis();
                    }
                }
            }

            getPersistenceManager().commit();
        } catch (Exception e) {
            getPersistenceManager().rollback();
            throw new ToolException(e.getMessage(), e, ToolException.TOOL_ERROR);
        }
    }

    private void findSingleSensorMatchups(String sensorName, Sensor sensor) {
        try {
            getPersistenceManager().transaction();

            int count = 0;
            long time = System.currentTimeMillis();
            final Query query = createIncrementalQuery(sensorName, SINGLE_SENSOR_OBSERVATION_QUERY);
            for (int cursor = 0; ; ) {
                final List<ReferenceObservation> observations = query.setFirstResult(cursor).getResultList();
                if (observations.size() == 0) {
                    break;
                }
                cursor += observations.size();

                for (final ReferenceObservation observation : observations) {
                    createMatchup(observation, sensor.getPattern());
                    ++count;
                    if (count % 1024 == 0) {
                        getPersistenceManager().commit();
                        getPersistenceManager().transaction();
                        getLogger().info(MessageFormat.format("{0} {1} processed in {2} ms.",
                                                              count,
                                                              sensor.getName(),
                                                              System.currentTimeMillis() - time));
                        time = System.currentTimeMillis();
                    }
                }
            }

            getPersistenceManager().commit();
        } catch (Exception e) {
            getPersistenceManager().rollback();
            throw new ToolException(e.getMessage(), e, ToolException.TOOL_ERROR);
        }
    }

    private void findRelatedObservations() {
        try {
            getPersistenceManager().transaction();

            long time = System.currentTimeMillis();
            final Query query = createIncrementalQuery(MATCHUPS_QUERY);
            for (int cursor = 0; ; ) {
                final List<Matchup> matchups = query.setFirstResult(cursor).getResultList();
                if (matchups.size() == 0) {
                    break;
                }

                final Properties configuration = getConfiguration();
                for (int i = 0; i < 100; i++) {
                    final String sensorName = configuration.getProperty(String.format("mms.source.%d.sensor", i));
                    if (sensorName == null) {
                        continue;
                    }
                    final Sensor sensor = getSensor(sensorName);
                    if (sensor == null) {
                        continue;
                    }
                    final Class<? extends Observation> observationClass = getObservationClass(sensor);
                    if (observationClass == ReferenceObservation.class) {
                        continue;
                    }
                    final String queryString = OBSERVATION_QUERY_MAP.get(observationClass);
                    if (queryString == null) {
                        final String message = MessageFormat.format("No query for observation type ''{0}''",
                                                                    observationClass.getSimpleName());
                        throw new ToolException(message, ToolException.TOOL_CONFIGURATION_ERROR);
                    }
                    int count = cursor;
                    for (final Matchup matchup : matchups) {
                        addCoincidence(matchup, sensorName, queryString, sensor.getPattern(), observationClass);
                        ++count;
                        if (count % 1024 == 0) {
                            getLogger().info(MessageFormat.format("{0} {1} processed in {2} ms.",
                                                                  count,
                                                                  sensorName,
                                                                  System.currentTimeMillis() - time));
                            time = System.currentTimeMillis();
                        }
                    }
                }

                getPersistenceManager().commit();
                getPersistenceManager().transaction();
                cursor += matchups.size();
            }
            getPersistenceManager().commit();
        } catch (Exception e) {
            getPersistenceManager().rollback();
            throw new ToolException(e.getMessage(), e, ToolException.TOOL_ERROR);
        }
    }

    private int configurationIndexOf(String sensor) {
        final Properties configuration = getConfiguration();
        for (int i = 0; i < 100; i++) {
            final String sensorName = configuration.getProperty(String.format("mms.source.%d.sensor", i));
            if (sensor.equals(sensorName)) {
                return i;
            }
        }
        return -1;
    }

    private Query createIncrementalQuery(String sensorName, String queryString) {
        final Query query = getPersistenceManager().createQuery(queryString);
        query.setParameter(1, sensorName);
        query.setParameter(2, getSourceStartTime());
        query.setParameter(3, getSourceStopTime());
        query.setMaxResults(CHUNK_SIZE);
        return query;
    }

    private Query createIncrementalQuery(String queryString) {
        final Query query = getPersistenceManager().createQuery(queryString);
        query.setParameter(1, getSourceStartTime());
        query.setParameter(2, getSourceStopTime());
        query.setMaxResults(CHUNK_SIZE);
        return query;
    }

    @SuppressWarnings({"unchecked"})
    private static Class<? extends Observation> getObservationClass(Sensor sensor) {
        try {
            return (Class<? extends Observation>) Class.forName(
                    String.format("%s.%s", Sensor.class.getPackage().getName(), sensor.getObservationType()));
        } catch (ClassCastException e) {
            throw new IllegalStateException(e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private void addCoincidence(Matchup matchup, String sensorName, String queryString,
                                long pattern, Class<? extends Observation> observationClass) {
        final ReferenceObservation refObs = matchup.getRefObs();
        final Query query = createObservationQuery(queryString, observationClass);
        final Observation sensorObs = findCoincidingObservation(refObs, query, sensorName);
        if (sensorObs != null) {
            final Coincidence coincidence = createCoincidence(matchup, sensorObs);
            matchup.setPattern(matchup.getPattern() | pattern);
        }
    }

    /**
     * Determines temporally nearest common observation of a specific sensor
     * for a reference observation.
     *
     * @param refObs The reference observation.
     * @param sensor The sensor.
     * @return common observation of sensor with coincidence to reference observation
     *         that is temporally closest to reference observation, null if no
     *         observation of the specified sensor has a coincidence with the
     *         reference observation
     */
    private Observation findCoincidingObservation(ReferenceObservation refObs, Sensor sensor) {
        final Class<? extends Observation> observationClass = getObservationClass(sensor);
        final Query query = createObservationQuery(COINCIDING_OBSERVATION_QUERY, observationClass);
        return findCoincidingObservation(refObs, query, sensor.getName());
    }

    private Query createObservationQuery(String queryString, Class<? extends Observation> resultClass) {
        return getPersistenceManager().createNativeQuery(queryString, resultClass);
    }

    private Observation findCoincidingObservation(ReferenceObservation refObs, Query query, String sensorName) {
        query.setParameter(1, refObs.getId());
        query.setParameter(2, sensorName);
        query.setMaxResults(1);
        @SuppressWarnings({"unchecked"})
        final List<? extends Observation> observations = query.getResultList();
        if (!observations.isEmpty()) {
            // select temporally nearest common observation
            return observations.get(0);
        } else {
            return null;
        }
    }


    /**
     * Factory method to create matchup for a reference observation.
     *
     * @param referenceObservation the reference observation constituting the matchup
     * @param pattern
     * @return the new Matchup for the reference observation
     */
    private Matchup createMatchup(ReferenceObservation referenceObservation, long pattern) {
        final Matchup matchup = new Matchup();
        matchup.setId(referenceObservation.getId());
        matchup.setRefObs(referenceObservation);
        matchup.setPattern(pattern);
        getPersistenceManager().persist(matchup);
        return matchup;
    }

    /**
     * Creates Coincidence between a matchup and a common observation,
     * determines temporal and spatial distance by a database query.
     *
     * @param matchup     the matchup with the reference observation
     * @param observation the common observation that has a coincidence with
     *                    the reference and is temporally closest to it
     * @return newly created Coincidence relating matchup and common observation
     */
    private Coincidence createCoincidence(Matchup matchup, Observation observation) {
        Assert.argument(observation instanceof Timeable, "!(observation instanceof Timeable)");
        final double timeDifference = TimeUtil.timeDifferenceInSeconds(matchup, (Timeable) observation);
        final Coincidence coincidence = new Coincidence();
        coincidence.setMatchup(matchup);
        coincidence.setObservation(observation);
        coincidence.setTimeDifference(timeDifference);
        getPersistenceManager().persist(coincidence);
        return coincidence;
    }

    private void cleanup() {
        getPersistenceManager().transaction();

        Query delete = getPersistenceManager().createQuery("delete from Coincidence c");
        delete.executeUpdate();
        delete = getPersistenceManager().createQuery("delete from Matchup m");
        delete.executeUpdate();

        getPersistenceManager().commit();
    }

}
