package core;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Balance implements Iterable<Task> {
	/** Equality wrapper for Task to be used in Set that compares only task names. */
	private static final class EqualityByNameTaskWrapper {
		private final Task task;

		private EqualityByNameTaskWrapper(final Task task) {
			this.task = task;
		}

		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			return Objects.equals(task.name, ((EqualityByNameTaskWrapper) o).task.name);
		}

		@Override
		public int hashCode() {
			return Objects.hashCode(task.name);
		}
	}

	private final Set<EqualityByNameTaskWrapper> tasks = new HashSet<>();

	private final Properties config;
	public Properties getConfig() { return config; }

	/** Tasks that were removed and whose save files are to be deleted on next save. */
	public final Set<String> pendingDelete = new HashSet<>();

	public Balance(final Properties config) {
		this.config = config;
	}

	/**
	 * Gets a task by name.
	 * @param name  the name of the desired task
	 * @return  the task with the specified name, or null if no such task exist
	 */
	public Task getTask(final String name) {
		for (final var task : this) {
			if (Objects.equals(task.name, name))
				return task;
		}
		return null;
	}

	/**
	 * Adds a task to this Balance if this Balance does not already contain another Task
	 * that {@link Task#equals} this task.
	 * @return	true if the task is added, false otherwise
	 */
	public boolean add(final Task task) {
		pendingDelete.remove(task.name);
		return tasks.add( new EqualityByNameTaskWrapper(task.init()) );
	}

	/**
	 * Removes the task with the specified name.
	 * @return	true if task was removed, false if task was not present
	 */
	public boolean remove(final String name) {
		pendingDelete.add(name);
		return tasks.removeIf(wrapper -> Objects.equals(wrapper.task.name, name));
	}


	@Override
	public Iterator<Task> iterator() {
		return adapt(tasks.iterator(), wrapper -> wrapper.task);
	}

	/** Adapts an Iterator of type T to produce type R according to mapping function. */
	private static <T, R> Iterator<R> adapt(
			final Iterator<T> it,
			final Function<? super T, ? extends R> mapping) {
		return new Iterator<>() {
			@Override
			public boolean hasNext() { return it.hasNext(); }
			@Override
			public R next() { return mapping.apply(it.next()); }
			// remove() not supported, cannot update pendingDelete
			@Override
			public void forEachRemaining(final Consumer<? super R> action) {
				// override because argument iterator might have custom implementation
				it.forEachRemaining( t -> action.accept(mapping.apply(t)) );
			}
		};
	}
}
