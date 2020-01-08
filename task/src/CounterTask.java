import core.Mutable;
import core.Task;

/**
 * A task with a counter that can be incremented or decremented.
 */
public class CounterTask extends Task {
	@Mutable
	public int counter, goal;

	public CounterTask(final String name, final int goal) {
		super(name);
		this.goal = goal;
	}

	@Override
	public void progress() {
		if (counter < goal)
			counter++;
	}

	@Override
	public String toString() {
		return super.toString() + '\t' +
				(counter >= goal ? "completed" : counter + "/" + goal);
	}
}
