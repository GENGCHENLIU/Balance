import core.Mutable;
import core.Task;

/**
 * A task that modifies its counter at some rate depending on its state.
 */
public class RateTask extends Task {
	// because the counter value is updated using a rate, the value may not be an integer
	private volatile double counter = 0;
	public double getCounter() { return counter; }
	private synchronized void update() { counter -= rate; }

	/** The rate at which the counter changes, in counts per second. */
	@Mutable
	public volatile double rate;

	public RateTask(final String name, final double rate) {
		super(name);
		this.rate = rate;
		start();
	}

	private boolean started = false;
	/** Starts updating this rate based task in a new thread. */
	public synchronized void start() {
		if (started) return;
		started = true;

		final var updater = new Thread(() -> {
			while (!Thread.currentThread().isInterrupted()) {
				try { Thread.sleep(1000); }
				catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				this.update();
			}
		}, "RateTask_" + name + "_UPDATER");
		updater.setDaemon(true);
		updater.start();
	}

	/**
	 * Makes progress on this RateTask.
	 */
	@Override
	public synchronized void progress() {
		counter++;
	}

	@Override
	public String toString() {
		return super.toString() + '\t' + counter + 'Δ' + rate;
	}
}
