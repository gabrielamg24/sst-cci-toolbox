package org.esa.cci.sst.common.file;

import org.esa.cci.sst.common.Aggregation;
import org.esa.cci.sst.common.ScalarGrid;
import org.esa.cci.sst.common.AggregationContext;
import org.esa.cci.sst.common.SstDepth;
import org.esa.cci.sst.common.calculator.CoverageUncertaintyProvider;
import org.esa.cci.sst.common.cell.AggregationCell;
import org.esa.cci.sst.common.cell.CellAggregationCell;
import org.esa.cci.sst.common.cell.CellFactory;
import org.esa.cci.sst.common.cell.SpatialAggregationCell;
import org.esa.cci.sst.common.cellgrid.Grid;
import org.esa.cci.sst.common.cellgrid.GridDef;
import org.esa.cci.sst.util.TestL3ProductMaker;
import org.junit.Test;
import ucar.nc2.NetcdfFile;

import java.awt.*;

import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import static junit.framework.Assert.*;
import static org.junit.Assert.assertNotNull;

/**
 * {@author Bettina Scholze}
 * Date: 17.09.12 11:02
 */
public class CciL4FileTypeTest {
    private static final FileType FILE_TYPE = CciL4FileType.INSTANCE;

    @Test
    public void testL3UCell5Aggregation() throws Exception {
        final GridDef sourceGridDef = FILE_TYPE.getGridDef();
        final AggregationContext context = new AggregationContext();
        context.setSstGrid(new ScalarGrid(sourceGridDef, 292.0));
        context.setRandomUncertaintyGrid(new ScalarGrid(sourceGridDef, 1.0));
        context.setSeaIceFractionGrid(new ScalarGrid(sourceGridDef, 0.5));
        context.setClimatologySstGrid(new ScalarGrid(sourceGridDef, 291.5));
        context.setSeaCoverageGrid(new ScalarGrid(sourceGridDef, 0.8));
        context.setCoverageUncertaintyProvider(new MockCoverageUncertaintyProvider(1.1, 1.2, 0.5));

        final CellFactory<SpatialAggregationCell> cell5Factory = FILE_TYPE.getCellFactory5(context);
        final SpatialAggregationCell cell5 = cell5Factory.createCell(0, 0);

        //execution
        cell5.accumulate(context, new Rectangle(0, 0, 100, 100));

        final int expectedN = 100 * 100;
        assertEquals(expectedN, cell5.getSampleCount());

        Number[] results = cell5.getResults();
        assertNotNull(results);
        assertEquals(8, results.length);
        assertEquals(292.0, results[Aggregation.SST].doubleValue(), 1.0e-6);
        assertEquals(0.5, results[Aggregation.SST_ANOMALY].doubleValue(), 1.0e-6);
        assertEquals((0.5 * 10000) / 10000, results[Aggregation.SEA_ICE_FRACTION].doubleValue(), 1e-6);
        assertEquals(1.2 * (1.0 - pow(expectedN / 77500.0, 0.5)), results[Aggregation.COVERAGE_UNCERTAINTY].doubleValue(), 1e-6);
        assertEquals(sqrt((0.8 * 0.8 * 10000) / ((0.8 * 10000) * (0.8 * 10000))), results[Aggregation.RANDOM_UNCERTAINTY].doubleValue(), 1e-6);
    }

    @Test
    public void testCell90Aggregation() throws Exception {
        final GridDef sourceGridDef = FILE_TYPE.getGridDef();
        final AggregationContext context = new AggregationContext();
        context.setSstGrid(new ScalarGrid(sourceGridDef, 292.0));
        context.setRandomUncertaintyGrid(new ScalarGrid(sourceGridDef, 0.1));
        context.setSeaIceFractionGrid(new ScalarGrid(sourceGridDef, 0.5));
        context.setClimatologySstGrid(new ScalarGrid(sourceGridDef, 291.5));
        context.setSeaCoverageGrid(new ScalarGrid(sourceGridDef, 0.8));
        context.setCoverageUncertaintyProvider(new MockCoverageUncertaintyProvider(1.1, 1.2, 0.5));

        final CellFactory<CellAggregationCell<AggregationCell>> cell90Factory = FILE_TYPE.getCellFactory90(context);
        final CellAggregationCell<AggregationCell> cell90 = cell90Factory.createCell(0, 0);
        final CellFactory<SpatialAggregationCell> cell5Factory = FILE_TYPE.getCellFactory5(context);

        final SpatialAggregationCell cell5_1 = cell5Factory.createCell(0, 0);
        cell5_1.accumulate(context, new Rectangle(0, 0, 100, 100));

        final SpatialAggregationCell cell5_2 = cell5Factory.createCell(1, 0);
        cell5_2.accumulate(context, new Rectangle(100, 0, 100, 100));

        final SpatialAggregationCell cell5_3 = cell5Factory.createCell(2, 0);
        cell5_3.accumulate(context, new Rectangle(200, 0, 100, 100));

        final SpatialAggregationCell cell5_4 = cell5Factory.createCell(3, 0);
        cell5_4.accumulate(context, new Rectangle(300, 0, 100, 100));

        int expectedN5_1 = 10000;
        int expectedN5_2 = 10000;
        int expectedN5_3 = 10000;
        int expectedN5_4 = 10000;
        assertEquals(expectedN5_1, cell5_1.getSampleCount());
        assertEquals(expectedN5_2, cell5_2.getSampleCount());
        assertEquals(expectedN5_3, cell5_3.getSampleCount());
        assertEquals(expectedN5_4, cell5_4.getSampleCount());

        cell90.accumulate(cell5_1, 0.25); // --> w=0.125, n = 100
        cell90.accumulate(cell5_2, 0.5);  // --> w=0.25, n = 100
        cell90.accumulate(cell5_3, 0.25); // --> w=0.125, n = 100
        cell90.accumulate(cell5_4, 1.0);  // --> w=0.5, n = 200

        final int expectedN90 = 4;
        assertEquals(expectedN90, cell90.getSampleCount());

        final Number[] results = cell90.getResults();
        assertNotNull(results);
        assertEquals(8, results.length);

        assertEquals(292.0, results[Aggregation.SST].doubleValue(), 1.0e-6);
        assertEquals(0.5, results[Aggregation.SST_ANOMALY].doubleValue(), 1.0e-6);
        // todo - replace inexplicable numbers by formulas, testCell5Aggregation() (nf)
        assertEquals(0.5, results[Aggregation.SEA_ICE_FRACTION].doubleValue(), 1e-6);
        assertEquals(0.7111627589581172, results[Aggregation.COVERAGE_UNCERTAINTY].doubleValue(), 1e-6);
        assertEquals(5.86301969977808E-4, results[Aggregation.RANDOM_UNCERTAINTY].doubleValue(), 1e-6);
    }


    @Test
    public void testReadSourceGrids() throws Exception {
        NetcdfFile l4File = TestL3ProductMaker.readL4GridsSetup();
        //execution
        final AggregationContext context = FILE_TYPE.readSourceGrids(l4File, SstDepth.skin, new AggregationContext());

        // analysed_sst
        final Grid sstGrid = context.getSstGrid();
        assertEquals(2000, sstGrid.getSampleInt(0, 0));
        assertEquals(293.14999344944954, sstGrid.getSampleDouble(0, 0));
        assertEquals(2000, sstGrid.getSampleInt(1, 0));
        assertEquals(293.14999344944954, sstGrid.getSampleDouble(1, 0));
        // analysis_error
        final Grid randomUncertaintyGrid = context.getRandomUncertaintyGrid();
        assertEquals(-32768, randomUncertaintyGrid.getSampleInt(0, 0));
        assertEquals(Double.NaN, randomUncertaintyGrid.getSampleDouble(0, 0));
        assertEquals(-32768, randomUncertaintyGrid.getSampleInt(1, 0));
        assertEquals(Double.NaN, randomUncertaintyGrid.getSampleDouble(1, 0));
        // sea_ice_fraction
        final Grid seaIceFractionGrid = context.getSeaIceFractionGrid();
        assertEquals(-128, seaIceFractionGrid.getSampleInt(0, 0));
        assertEquals(Double.NaN, seaIceFractionGrid.getSampleDouble(0, 0));
        assertEquals(-128, seaIceFractionGrid.getSampleInt(1, 0));
        assertEquals(Double.NaN, seaIceFractionGrid.getSampleDouble(1, 0));
    }

    @Test
    public void testFileNameRegex() throws Exception {
        assertFalse("Hallo".matches(FILE_TYPE.getFilenameRegex()));
        assertFalse("ATS_AVG_3PAARC_20020915_D_nD3b.nc.gz".matches(FILE_TYPE.getFilenameRegex()));
        assertFalse("19950723120045-ESACCI-L3C_GHRSST-SSTskin-AATSR-DM-v02.0-fv01.0.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertFalse("20100701000000-ESACCI-L3U_GHRSST-SSTsubskin-AMSRE-LT-04.1-01.1.nc".matches(FILE_TYPE.getFilenameRegex()));

        assertTrue("20100701000000-ESACCI-L4_GHRSST-SSTskin-AATSR-DM-v02.0-fv01.0.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertTrue("20100701000000-ESACCI-L4_GHRSST-SSTskin-AATSR-LT-v02.0-fv01.0.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertTrue("19950723120045-ESACCI-L4_GHRSST-SSTdepth-AATSR-DM-v02.0-fv01.0.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertTrue("20100701000000-ESACCI-L4_GHRSST-SSTfnd-ATSR1-LT-v04.1-fv01.1.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertTrue("20121101000000-ESACCI-L4_GHRSST-SSTsubskin-ATSR2-LT-v04.1-fv01.1.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertTrue("20100701000000-ESACCI-L4_GHRSST-SSTsubskin-AMSRE-LT-v04.1-fv01.1.nc".matches(FILE_TYPE.getFilenameRegex()));
        assertTrue("20100701000000-ESACCI-L4_GHRSST-SSTsubskin-SEVIRI_SST-LT-v04.1-fv01.1.nc".matches(FILE_TYPE.getFilenameRegex()));
    }
}