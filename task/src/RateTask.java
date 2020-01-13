import core.Mutable;
import core.TimeDependentTask;

/**
 * A task that modifies its counter at some rate depending on its state.
 */
public class RateTask extends TimeDependentTask {
	// because the counter value is updated using a rate, the value may not be an integer
	private volatile double counter = 0;
	public double getCounter() { return counter; }
	@Override
	public synchronized void update() { counter -= rate; }
	@Override
	public synchronized void update(final long count) { counter -= count * rate; }

	/** The rate at which the counter changes, in counts per second. */
	@Mutable
	public volatile double rate;

	public RateTask(final String name, final double rate) {
		super(name);
		this.rate = rate;
	}

	/** Makes progress on this RateTask. */
	@Override
	public synchronized void progress() { counter++; }

	@Override
	public String toString() {
		return super.toString() + '\t' + counter + 'Δ' + rate;
	}
}
