package de.ust.skill.ir.restriction;

/**
 * @note In contrast to IntRangeRestriction, the result depends upon the fields
 *       type.
 * @note The code relies on the invariant that low and high are actual numbers
 *       excluding "-0.0"
 * @author Timm Felden
 */
public class FloatRangeRestriction extends RangeRestriction {

	private final double low, high;
	private final boolean inclusiveLow, inclusiveHigh;

	public FloatRangeRestriction(double low, double high, boolean inclusiveLow, boolean inclusiveHigh) {
		this.low = low;
		this.inclusiveLow = inclusiveLow;
		this.high = high;
		this.inclusiveHigh = inclusiveHigh;

		if (this.low >= this.high)
			throw new IllegalStateException("Integer range restriction has no legal values: " + this.low + " -> "
					+ this.high);
	}

	/**
	 * @return lowest legal value; always inclusive
	 */
	public double getLowDouble() {
		if (inclusiveLow)
			return low;
		return Double.longBitsToDouble(Double.doubleToLongBits(low) + 1L);
	}

	/**
	 * @return lowest legal value; always inclusive
	 */
	public float getLowFloat() {
		if (inclusiveLow)
			return (float) low;
		return Float.intBitsToFloat((Float.floatToIntBits((float) low) + 1));
	}

	/**
	 * @return highest legal value; always inclusive
	 */
	public double getHighDouble() {
		if (inclusiveHigh)
			return high;
		return Double.longBitsToDouble(Double.doubleToLongBits(high) - 1L);
	}

	/**
	 * @return highest legal value; always inclusive
	 */
	public float getHighFloat() {
		if (inclusiveHigh)
			return (float) high;
		return Float.intBitsToFloat((Float.floatToIntBits((float) high) - 1));
	}
}
