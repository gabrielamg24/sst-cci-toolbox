package org.esa.cci.sst.regavg;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Norman Fomferra
 */
public class RegionalAverageToolTest {

    @Test
    public void testOutputName() throws Exception {
        String filename = RegionalAverageTool.getOutputFilename("20000101", "20101231", "Global", ProcessingLevel.L3U, "SSTskin", "PS", "DM");
        assertEquals("20000101-20101231-Global_average-ESACCI-L3U_GHRSST-SSTskin-PS-DM-v1.0_b03-fv1.1.nc", filename);
    }

}
