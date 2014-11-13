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

package org.esa.cci.sst.common.cell;

import org.esa.cci.sst.common.AbstractAggregation;
import org.esa.cci.sst.aggregate.AggregationCell;
import org.esa.cci.sst.aggregate.AggregationContext;

/**
 * An abstract aggregation cell.
 *
 * @author Norman Fomferra
 */
public abstract class AbstractAggregationCell extends AbstractAggregation implements AggregationCell {

    private final int x;
    private final int y;
    private final AggregationContext aggregationContext;

    protected AbstractAggregationCell(AggregationContext aggregationContext, int x, int y) {
        this.x = x;
        this.y = y;
        this.aggregationContext = aggregationContext;
    }

    @Override
    public final int getX() {
        return x;
    }

    @Override
    public final int getY() {
        return y;
    }

    @Override
    public final boolean isEmpty() {
        return getSampleCount() == 0;
    }

    public final AggregationContext getAggregationContext() {
        return aggregationContext;
    }
}
