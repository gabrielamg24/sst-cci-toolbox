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

package org.esa.cci.sst.regavg;

import org.esa.cci.sst.util.Grid;
import org.esa.cci.sst.util.GridDef;

/**
 * Provides the input grids for an {@link AggregationCell5}.
 *
 * @author Norman Fomferra
*/
public final class AggregationCell5Context {
    private final GridDef sourceGridDef;
    private final Grid[] sourceGrids;
    private final Grid analysedSstGrid;
    private final Grid seaCoverageGrid;

    public AggregationCell5Context(GridDef sourceGridDef, Grid[] sourceGrids, Grid analysedSstGrid, Grid seaCoverageGrid) {
        this.sourceGridDef = sourceGridDef;
        this.sourceGrids = sourceGrids;
        this.analysedSstGrid = analysedSstGrid;
        this.seaCoverageGrid = seaCoverageGrid;
    }

    public GridDef getSourceGridDef() {
        return sourceGridDef;
    }

    public Grid[] getSourceGrids() {
        return sourceGrids;
    }

    public Grid getAnalysedSstGrid() {
        return analysedSstGrid;
    }

    public Grid getSeaCoverageGrid() {
        return seaCoverageGrid;
    }
}
