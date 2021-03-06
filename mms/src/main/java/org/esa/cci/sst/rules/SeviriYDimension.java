package org.esa.cci.sst.rules;

/**
 * Replaces the second dimension with 'seviri.ny'.
 *
 * @author Ralf Quast
 */
final class SeviriYDimension extends AbstractDimensionReplacement {

    @Override
    protected void replaceDimensions(DimensionStringBuilder builder) throws RuleException {
        builder.replace(1, "seviri.ny");
    }
}
