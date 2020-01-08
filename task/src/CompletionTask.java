import core.Mutable;
import core.Task;

/**
 * A one time task who's only state is incomplete and completed.
 */
public class CompletionTask extends Task {
	@Mutable
	public boolean isCompleted = false;

	public CompletionTask(final String name) {
		super(name);
	}

	@Override
	public void progress() { isCompleted = true; }

	@Override
	public String toString() {
		return super.toString() + '\t' + (isCompleted ? "completed" : "incomplete");
	}
}
