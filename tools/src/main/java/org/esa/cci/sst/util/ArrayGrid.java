package org.esa.cci.sst.util;

import ucar.ma2.Array;

/**
 * A {@link Grid} that is backed by a NetCDF {@code Array} instance.
 *
 * @author Norman Fomferra
 */
public class ArrayGrid implements Grid {
    final GridDef gridDef;
    final Array array;
    final double scaling;
    final double offset;
    final Number fillValue;
    final FillTest fillTest;

    public ArrayGrid(GridDef gridDef, double scaling, double offset, final Number fillValue, Array array) {
        this.gridDef = gridDef;
        this.array = array;
        this.scaling = scaling;
        this.offset = offset;
        this.fillValue = fillValue;
        this.fillTest = createFillTest(fillValue);
    }

    public Array getArray() {
        return array;
    }

    @Override
    public GridDef getGridDef() {
        return gridDef;
    }

    @Override
    public boolean getSampleBoolean(int x, int y) {
        int index = y * gridDef.getWidth() + x;
        return array.getBoolean(index);
    }

    @Override
    public int getSampleInt(int x, int y) {
        int index = y * gridDef.getWidth() + x;
        return array.getInt(index);
    }

    @Override
    public double getSampleDouble(int x, int y) {
        int index = y * gridDef.getWidth() + x;
        double sample = array.getDouble(index);
        if (fillTest.isFill(sample)) {
            return Double.NaN;
        }
        return scaling * sample + offset;
    }

    public ArrayGrid scaleDown(int scaleX, int scaleY) {
        GridDef newGridDef = new GridDef(gridDef.getWidth() / scaleX,
                                         gridDef.getHeight() / scaleY,
                                         gridDef.getEasting(),
                                         gridDef.getNorthing(),
                                         gridDef.getResolutionX() * scaleX,
                                         gridDef.getResolutionY() * scaleY);
        Array newArray = Array.factory(array.getElementType(), new int[] {newGridDef.getHeight(), newGridDef.getWidth()});
        ArrayGrid newArrayGrid = new ArrayGrid(newGridDef, 1.0, 0.0, null, newArray);
        for (int yd = 0; yd < newGridDef.getHeight(); yd++) {
            for (int xd = 0; xd < newGridDef.getWidth(); xd++) {
                double sum = 0;
                int n = 0;
                for (int dy = 0; dy < scaleY; dy++) {
                    int ys = yd * scaleY + dy;
                    for (int dx = 0; dx < scaleX; dx++) {
                        int xs = xd * scaleX + dx;
                        double sample = getSampleDouble(xs, ys);
                        if (!Double.isNaN(sample)) {
                            sum += sample;
                            n++;
                        }
                    }
                }
                newArrayGrid.setSample(xd, yd, n > 0 ? sum / n  : Double.NaN);
            }
        }
        return new ArrayGrid(newGridDef, scaling, offset, fillValue, newArray);
    }

    private void setSample(int x, int y, double sample) {
        int index = y * gridDef.getWidth() + x;
        array.setDouble(index, sample);
    }

    private interface FillTest {
        boolean isFill(double sample);
    }

    private static FillTest createFillTest(Number fillValue) {

        if (fillValue == null) {
            return new FillTest() {
                @Override
                public boolean isFill(double sample) {
                    return false;
                }
            };
        } else {
            final double fillSample = fillValue.doubleValue();
            if (Double.isNaN(fillSample)) {
                return new FillTest() {
                    @Override
                    public boolean isFill(double sample) {
                        return Double.isNaN(sample);
                    }
                };
            } else {
                return new FillTest() {
                    @Override
                    public boolean isFill(double sample) {
                        return sample == fillSample;
                    }
                };
            }
        }
    }

    public ArrayGrid resample(GridDef targetGridDef) {
        int sourceWidth = gridDef.getWidth();
        int sourceHeight = gridDef.getHeight();
        int targetWidth = targetGridDef.getWidth();
        int targetHeight = targetGridDef.getHeight();
        // todo implement resampling by averaging
        // System.out.println("ArrayGrid.resample() --> NOT IMPLEMENTED YET!");
        return this;
    }

}
