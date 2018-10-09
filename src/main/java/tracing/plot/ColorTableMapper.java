package tracing.plot;

import org.jzy3d.colors.Color;
import org.jzy3d.colors.ColorMapper;

import net.imagej.display.ColorTables;
import net.imglib2.display.ColorTable;

class ColorTableMapper extends ColorMapper {

	private ColorTable colorTable;
	private double min;
	private double max;

	public ColorTableMapper(final ColorTable colorTable, final double min, final double max) {
		super();
		this.colorTable = (colorTable == null) ? ColorTables.ICE : colorTable;
		this.min = min;
		this.max = max;
	}

	@Override
	public Color getColor(final double mappedValue) {
		final int idx;
		if (mappedValue <= min)
			idx = 0;
		else if (mappedValue > max)
			idx = colorTable.getLength() - 1;
		else
			idx = (int) Math.round((colorTable.getLength() - 1) * (mappedValue - min) / (max - min));
		return new Color(colorTable.get(ColorTable.RED, idx), colorTable.get(ColorTable.GREEN, idx),
				colorTable.get(ColorTable.BLUE, idx));
	}

	@Override
	public double getMin() {
		return min;
	}

	@Override
	public double getMax() {
		return max;
	}

	@Override
	public void setMin(double min) {
		this.min = min;
	}

	@Override
	public void setMax(double max) {
		this.max = max;
	}

}
