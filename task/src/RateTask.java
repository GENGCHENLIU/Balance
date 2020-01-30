import core.Mutable;
import core.TimeDependentTask;

/**
 * A Task that increases or decreases a counter based on its state.
 * A RateTask may be active or inactive. In the active state, the counter is increased
 * at a user specified rate; in the inactive state, the counter decreases at 1 count per
 * second.
 */
public class RateTask extends TimeDependentTask {
	private double counter = 0;

	@Mutable
	public volatile double rate;
	@Mutable
	public boolean active = false;

	/**
	 * @param activeRate	rate at which the counter is increased in the active state
	 */
	public RateTask(final String name, final double activeRate) {
		super(name);
		rate = activeRate;
	}

	@Override
	public synchronized void update() {
		counter += active ? rate : -1;
	}
	@Override
	public synchronized void update(final long count) {
		counter += active ? rate * count : -count;
	}

	@Override
	public void progress() {
		active = true;
	}
}
