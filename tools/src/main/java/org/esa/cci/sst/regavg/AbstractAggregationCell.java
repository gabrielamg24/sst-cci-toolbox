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

import org.esa.cci.sst.util.AbstractCell;
import org.esa.cci.sst.util.AggregationCell;

/**
 * A daily or monthly / 5º or 90º cell.
 *
 * @author Norman Fomferra
 */
public abstract class AbstractAggregationCell extends AbstractCell implements AggregationCell {
    private final CoverageUncertaintyProvider coverageUncertaintyProvider;

    protected AbstractAggregationCell(CoverageUncertaintyProvider coverageUncertaintyProvider, int x, int y) {
        super(x,y);
        this.coverageUncertaintyProvider = coverageUncertaintyProvider;
    }

    public final CoverageUncertaintyProvider getCoverageUncertaintyProvider() {
        return coverageUncertaintyProvider;
    }

    @Override
    public boolean isEmpty() {
        return getSampleCount() == 0;
    }
}