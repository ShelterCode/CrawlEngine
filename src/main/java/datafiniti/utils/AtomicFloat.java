package datafiniti.utils;

import static java.lang.Float.floatToIntBits;
import static java.lang.Float.intBitsToFloat;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Atomic Float for used atomic multithreaded float calculations
 * 
 * @author markn
 *
 */
public class AtomicFloat extends Number {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -4249175336840161629L;
	
	/**
	 * use an AtomicInteger to hold byte values, and cast to float
	 */
	private AtomicInteger bits;

	/**
	 * default constructor init to zero
	 */
	public AtomicFloat() {
		this(0f);
	}

	/**
	 * accumulate a sum
	 * 
	 * @param newValue
	 */
	public void accumulate(float newValue) {
		set(get() + newValue);
	}

	/**
	 * accumulate and average
	 * 
	 * @param newValue
	 */
	public void accumulateAverage(float newValue) {
		set((get() + newValue) / 2);
	}

	/**
	 * init with param
	 * 
	 * @param initialValue
	 */
	public AtomicFloat(float initialValue) {
		bits = new AtomicInteger(floatToIntBits(initialValue));
	}

	/**
	 * set if value is expected value
	 * 
	 * @param expect value that is to be in atomic float currently
	 * @param update new value to use if it has expected value
	 * @return
	 */
	public final boolean compareAndSet(float expect, float update) {
		return bits.compareAndSet(floatToIntBits(expect), floatToIntBits(update));
	}

	/**
	 * set a new value to AtomicFloat
	 * 
	 * @param newValue
	 */
	public final void set(float newValue) {
		bits.set(floatToIntBits(newValue));
	}

	/**
	 * get atomic floats current value
	 * 
	 * @return
	 */
	public final float get() {
		return intBitsToFloat(bits.get());
	}

	/**
	 * get old value and set new value
	 * 
	 * @param newValue
	 * @return
	 */
	public final float getAndSet(float newValue) {
		return intBitsToFloat(bits.getAndSet(floatToIntBits(newValue)));
	}

	/**
	 * weak compare and set to support deprecated method
	 * 
	 * @param expect
	 * @param update
	 * @return
	 */
	@Deprecated(since = "9")
	public final boolean weakCompareAndSet(float expect, float update) {
		return bits.weakCompareAndSet(floatToIntBits(expect), floatToIntBits(update));
	}

	public double doubleValue() {
		return (double) floatValue();
	}

	public int intValue() {
		return (int) get();
	}

	public long longValue() {
		return (long) get();
	}

	@Override
	public float floatValue() {
		
		return get();
	}

	
	  

}