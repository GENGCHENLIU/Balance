import core.Mutable;
import core.TimeDependentTask;

/**
 * A task that modifies its counter at some rate.
 * Each {@link #progress()} increments the counter, and only invocations of a sufficient
 * frequency would be able to balance the decrements from {@link #update()}.
 */
public class FrequencyTask extends TimeDependentTask {
	// because the counter value is updated using a rate, the value may be not an integer
	private volatile double counter = 0;
	public double getCounter() { return counter; }
	@Override
	public synchronized void update() { counter -= rate; }
	@Override
	public synchronized void update(final long count) { counter -= count * rate; }

	/** The rate at which the counter changes, in counts per second. */
	@Mutable
	public volatile double rate;

	public FrequencyTask(final String name, final double rate) {
		super(name);
		this.rate = rate;
	}

	/** Increments the counter of this task by 1. */
	@Override
	public synchronized void progress() { counter++; }

	@Override
	public String toString() {
		return super.toString() + '\t' + counter + 'Δ' + rate;
	}
}
