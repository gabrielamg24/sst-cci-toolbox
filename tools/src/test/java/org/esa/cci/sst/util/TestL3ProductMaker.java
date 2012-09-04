package org.esa.cci.sst.util;

import org.esa.cci.sst.common.cellgrid.ArrayGrid;
import org.esa.cci.sst.regrid.SpatialResolution;
import ucar.nc2.NetcdfFile;

import java.io.IOException;
import java.util.Map;

/**
 * @author Bettina Scholze
 *         Date: 26.07.12 10:30
 */
public class TestL3ProductMaker {

    public static Map<String, ArrayGrid> fetchL3UProductForTest() throws IOException {
        NetcdfFile file = readL3GridsSetup();
        return NcUtils.readL3Grids(file, SpatialResolution.DEGREE_0_05.getAssociatedGridDef());
    }

    public static NetcdfFile readL3GridsSetup() throws IOException {
        String userDirOrigin = System.getProperty("user.dir");
        String userDir = userDirOrigin.replace("\\", "/");
        String path = userDir.concat("/src/test/resources/org/esa/cci/sst/util/20100701000000-ESACCI-L3U_GHRSST-SSTskin-AATSR-LT-v02.0-fv01.0.nc");
        return NetcdfFile.open(path);
    }
}