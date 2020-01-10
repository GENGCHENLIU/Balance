package core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * The root interface of Tasks.
 * <p>
 * For a constructor of a Task to be invokable through the create command, only
 * {@code int}, {@link Integer}, {@code long}, {@link Long}, {@code double},
 * {@link Double}, and {@link String} parameters are allowed. During Task creation,
 * constructors are considered in the order of declaration; constructors with parameters
 * taking any other types are not considered.
 * <p>
 * All custom Task types must register themselves through {@link #register(Class)} or
 * {@link #loadAndRegister(Path)}.
 */
public abstract class Task {
	/** Registered types of Task. */
	private static transient final Set<Class<? extends Task>> TYPES = new HashSet<>();
	/** @return an unmodifiable view of the loaded types of Task */
	public static Set<Class<? extends Task>> getTypes() {
		return Collections.unmodifiableSet(TYPES);
	}

	/**
	 * @return	the Class of the Task with the matching name, or null if no such Class
	 * exist.
	 */
	public static Class<? extends Task> getType(final String name) {
		for (final var type : TYPES) {
			if (Objects.equals(type.getSimpleName(), name))
				return type;
		}
		return null;
	}

	/**
	 * Registers the specified type such that the Task may be used reflectively. Repeated
	 * registration has no effect.
	 * @return	true if the type is successfully registered, false otherwise
	 */
	public static boolean register(final Class<? extends Task> taskType) {
		// add only non-null
		return taskType != null && TYPES.add(taskType);
	}

	/**
	 * Loads a Task class from the specified class file and registers the loaded type.
	 * @param classFile	path to the class file containing a Task type definition
	 * @return the Class representing the loaded Task type
	 * @throws IOException	an I/O error occurs reading from the stream
	 * @throws ClassFormatError	the data did not contain a valid class
	 * @throws ClassCastException	the loaded class is not a subtype of Task
	 */
	public static boolean loadAndRegister(final Path classFile)
			throws IOException, ClassFormatError, ClassCastException {
		// read the class file
		final Class<?> cls = TaskLoader.INSTANCE.define(Files.readAllBytes(classFile));

		// ensure it is a Task
		if (!Task.class.isAssignableFrom(cls))
			throw new ClassCastException("Class is not a subtype of Task: " + cls.getName());

		return register((Class<? extends Task>) cls);
	}


	@Mutable
	public String name;
	protected Task(final String name) {
		this.name = name;
	}
	/**
	 * Performs additional initialization in addition to the constructor. The default
	 * implementation does nothing and simply returns this instance.
	 * <p>
	 * Fields do not have the proper values when accessed in the constructors during
	 * deserialization. Initialization operations that use instance fields should be
	 * placed in this method. Overriding methods should guard against repeated invocations
	 * as necessary.
	 * @return this instance
	 */
	public Task init() { return this; }

	/**
	 * Makes progress on this task. The definition of which may differ among different
	 * types of tasks.
	 */
	public abstract void progress();

	@Override
	public String toString() {
		return this.getClass().getSimpleName() + " '" + name + "'";
	}
}
