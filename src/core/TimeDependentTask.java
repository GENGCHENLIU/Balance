package core;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task that updates itself at a regular interval.
 */
public abstract class TimeDependentTask extends Task {
	private static transient final ScheduledExecutorService executor =
			Executors.newSingleThreadScheduledExecutor(runnable -> {
				final var t = new Thread(runnable);
				t.setDaemon(true);
				return t;
			});

	/** Interval in milliseconds at which {@link #update()} is called. */
	protected final int interval;
	/** Milliseconds since epoch of last update(); used for catching up after file load. */
	private volatile long lastUpdate = System.currentTimeMillis();
	
	/**
	 * Equivalent to {@code TimeDependentTask(name, 1000)}.
	 * @see	#TimeDependentTask(String, int)
	 */
	protected TimeDependentTask(final String name) {
		this(name, 1000);
	}

	/**
	 * @param interval	milliseconds between each {@link #update()} call
	 */
	protected TimeDependentTask(final String name, final int interval) {
		super(name);
		this.interval = interval;
	}

	private transient boolean initted = false;
	/**
	 * Initializes this task to repeatedly {@link #update()} at specified
	 * {@link #interval}. This method also caches up missed updates since program exit.
	 * Invocations after the first has no effect.
	 * @see Task#init()
	 */
	@Override
	public synchronized Task init() {
		if (initted) return this;
		initted = true;

		// non-trivial number of missed updates if loading from file
		final var updatesMissed = (System.currentTimeMillis() - lastUpdate) / interval;
		if (updatesMissed > 0) update(updatesMissed);

		executor.scheduleAtFixedRate(() -> {
			update();
			lastUpdate = System.currentTimeMillis();
		}, 0, interval, TimeUnit.MILLISECONDS);

		return this;
	}

	/** Perform some update, invoked every {@link #interval} milliseconds. */
	public abstract void update();
	/**
	 * {@link #update()} count times through a simple loop. Subclasses are recommended to
	 * override this method if a more efficient implementation of repeated update is
	 * available.
	 */
	public void update(long count) {
		for (long i = 0; i < count; i++)
			update();
	}
}
