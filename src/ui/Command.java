package ui;

import com.cedarsoftware.util.io.JsonWriter;
import core.Mutable;
import core.Task;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// can't declare this constant in enum due to illegal forward reference
import static ui.LineSeparator.LINE_SP;
final class LineSeparator {
	public static final String LINE_SP = System.getProperty("line.separator");
}

enum Command {
	QUIT() {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) { return false; }
	},
	EXIT(
			"exit" + LINE_SP +
					"\tExit the program."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) { return false; }
	},

	LIST(
			"list [TASK]..." + LINE_SP +
					"\tList current tasks. If an argument is supplied, list the properties" + LINE_SP +
					"\tof the specified tasks."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			if (args.size() == 0) {  // if only command, no argument
				// list all tasks
				for (final var task : balance)
					System.err.println(task);
			}
			else {  // list properties of each specified task
				for (final String name : args) {
					final var task = balance.getTask(name);

					if (task == null) {
						System.err.println("Task '" + name + "' does not exist");
						continue;
					}

					System.err.println(task);
					// reflectively access fields marked with @Mutable
					for (final var field : task.getClass().getFields()) {
						if (!isEditable(field))
							continue;

						try {
							System.err.printf("\t%s %s:\t%s%n",
									field.getType().getSimpleName(), field.getName(), field.get(task));
						}
						catch (IllegalAccessException ignored) {}
					}
				}
			}
			return true;
		}
	},

	SAVE(
			"save" + LINE_SP +
					"\tSaves all tasks to file. Saved tasks can be loaded on next launch."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			final var saveDir = balance.getConfig().getProperty("save_dir");
			final Path savePath;
			try { savePath = Path.of(saveDir); }
			catch (InvalidPathException e) {
				System.err.println("Save failed: save path is invalid: " + saveDir);
				return true;
			}

			for (final var taskName : balance.pendingDelete) {
				try { Files.deleteIfExists(Path.of(taskName)); }
				catch (Exception e) {
					Balance.error("Failed to delete old save file: " + taskName,
							e, true);
				}
			}

			// serialize and write each task
			for (final var task : balance) {
				final var name = task.name;
				final String json = JsonWriter.objectToJson(task);
				final Path saveFile = savePath.resolve(name);

				try { Files.writeString(saveFile, json); }
				catch (Exception e) {
					Balance.error("Save failed", e, true);
				}
			}

			return true;
		}
	},

	CREATE(
			"create TYPE [ARGUMENT]..." + LINE_SP +
					"\tCreates a new task of the specified type. The constructors of" + LINE_SP +
					"\tthe specified type is considered in the order of declaration" + LINE_SP +
					"\tand the first complete match is invoked."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			if (args.size() < 1) {
				System.err.println(this.usage);
				return true;
			}

			final var type = Task.getType(args.get(0));
			if (type == null) {
				System.err.println("Unknown task type: " + args.get(0));
				System.err.println("Use help to see available types");
				return true;
			}

			Task task = construct(type, args.subList(1, args.size()));

			if (task == null)	// no matching constructor or exception
				System.err.println("Construction failed");
			else {
				if (!balance.add(task))
					System.err.println("Task already exist: " + task.name);
			}
			return true;
		}

		/**
		 * Tries to construct the specified type of Task using the given arguments,
		 * performing any string to numeral conversion as necessary.
		 * @return	a new Task of specified type, or null if construction failed
		 */
		private Task construct(final Class<? extends Task> type, final List<String> args) {
			// for each public constructor
			nextConstructor: for (final var constructor : type.getConstructors()) {
				final var params = constructor.getParameters();
				if (params.length != args.size())
					continue;

				final Object[] argsCasted = new Object[params.length];
				// for each param-arg pair, try conversion if necessary
				for (int i = 0; i < params.length; i++) {
					final var expected = params[i].getType();
					final var argStr = args.get(i);

					try { argsCasted[i] = parse(argStr, expected); }
					catch (IllegalArgumentException e) { continue nextConstructor; }
				}

				try { return (Task) constructor.newInstance(argsCasted); }
				catch (Exception ignored) {}
			}

			return null;
		}
	},

	EDIT(
			"edit TASK [KEY=VALUE]..." + LINE_SP +
					"\tEdits the specified task. "
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			if (args.size() < 1) {
				System.err.println(this.usage);
				return true;
			}

			if (args.size() < 2)
				return LIST.execute(balance, args);

			final var task = balance.getTask(args.get(0));
			if (task == null) {
				System.err.println("Task '" + args.get(0) + "' does not exist");
				return true;
			}

			final var kvPairs = args.subList(1, args.size());

			// first pass, check all entries are valid and prepare for modification
			// only carry out modification if all entries are valid
			boolean anyInvalid = false;
			final var taskType = task.getClass();
			final var fieldToVal = new HashMap<Field, Object>();
			for (final String entry : kvPairs) {
				if (!entry.contains("=")) {
					System.err.println("Argument is not KEY=VALUE pair: " + entry);
					anyInvalid = true;
					continue;
				}

				// split only at first =
				final var kv = entry.split("=", 2);
				final String fieldName = kv[0], valStr = kv[1];

				// check field
				Field field;	// null if does not exist or not @Mutable public non-final
				try {
					field = taskType.getField(fieldName);
					if (field.getAnnotation(Mutable.class) == null ||
							Modifier.isFinal(field.getModifiers()))
						field = null;
				}
				catch (NoSuchFieldException e) { field = null; }

				if (field == null) {
					System.err.println("Not a @Mutable public non-final field: " + fieldName);
					anyInvalid = true;
					continue;
				}

				// check value, try to parse string to field type
				final Object value;
				try { value = parse(valStr, field.getType()); }
				catch (Exception e) {
					System.err.print("Illegal value: " + valStr);
					final var msg = e.getMessage();
					if (msg != null && !msg.isEmpty())
						System.err.print(": " + e.getMessage());
					System.err.println();
					anyInvalid = true;
					continue;
				}

				fieldToVal.merge(field, value, (oldVal, newVal) -> newVal);
			}

			if (anyInvalid)
				return true;

			// all args valid, modify
			for (final var kv : fieldToVal.entrySet()) {
				final var field = kv.getKey();
				final var value = kv.getValue();

				try { field.set(task, value); }
				catch (Exception | ExceptionInInitializerError e) {
					System.err.println("Failed to set field '" + field.getName() + "' to '" + value + "'");
					e.printStackTrace();
					System.err.println("Operation aborted");
				}
			}

			return true;
		}


	},

	RM(
			"rm TASK..." + LINE_SP +
					"\tRemove tasks."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			if (args.size() < 1) {
				System.err.println(this.usage);
				return true;
			}

			for (final var task : args) {
				if (!balance.remove(task))
					System.err.println("Task '" + task + "' does not exist");
			}
			return true;
		}
	},

	PROGRESS(
			"progress TASK..." + LINE_SP +
					"\tMake progress on the specified tasks."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			if (args.size() < 1) {
				System.err.println(this.usage);
				return true;
			}

			for (final var taskName : args) {
				final var task = balance.getTask(taskName);
				if (task != null)
					task.progress();
				else
					System.err.println("Task '" + taskName + "' does not exist");
			}
			return true;
		}
	},

	HELP(
			"help" + LINE_SP +
					"\tDisplay help text."
	) {
		@Override
		public boolean execute(final core.Balance balance, final List<String> args) {
			System.err.println("COMMANDS:");
			for (final var c : Command.values())
				System.err.println(c.usage);
			System.err.println();
			System.err.println("TASK TYPES:");
			for (final var type : Task.getTypes()) {
				System.err.println(type.getSimpleName());
				for (final var constructor : type.getConstructors()) {
					/*
					Constructor parameter names are printed as part of the help
					message. Subclasses of Task must be compiled with -parameters
					option for this information to be available at runtime.
					 */
					final String params = Stream.of(constructor.getParameters())
							// class_name<space>param_name
							.map(p -> p.getType().getSimpleName() + ' ' + p.getName())
							.collect(Collectors.joining(", "));
					System.err.println("\t" + constructor.getName() + "( " + params + " )");
				}
			}
			return true;
		}
	};


	/** Usage text of this command. */
	protected final String usage;

	/** The name of the command. Lower case of the constant's name. */
	private final String name = name().toLowerCase();
	public String getCmdName() { return name; }


	Command() { usage = name; }
	Command(final String usage) {
		this.usage = usage;
	}

	/**
	 * Returns the Command with the specified name. This method differs from
	 * {@link #valueOf(String)} in two ways: the name argument compares against the
	 * lowercase of the name used in the declaration of the constant; and when the
	 * command of the specified name is not found, null is returned instead of
	 * throwing an exception.
	 * @param name  the name of the desired command
	 * @return  the desired command, or null if command has the specified name.
	 */
	public static Command valueOfCmd(final String name) {
		for (final var cmd : values()) {
			if (cmd.getCmdName().equals(name))
				return cmd;
		}
		return null;
	}

	/**
	 * Execute this command.
	 * @param balance   the {@link core.Balance} instance to operate on
	 * @param args   the command arguments
	 * @return  false to signal exit, true to continue.
	 */
	public abstract boolean execute(final core.Balance balance, final List<String> args);

	/** A convenience method for commands that do not take arguments. */
	public boolean execute() { return execute(Balance.BALANCE, Collections.emptyList()); }

	//////////
	// UTILS
	//////////

	/**
	 * Parse the string and cast into the appropriate type if possible.
	 * @param arg	the string to parse
	 * @param target	the type to cast to. Only int, long, double, String, or the
	 *                  corresponding wrapper type is legal.
	 * @return	the parsed and casted result
	 * @throws NumberFormatException	target is a numeric type and the argument
	 * string is not a number
	 * @throws IllegalArgumentException	cannot be casted to target type
	 */
	private static <T> T parse(final String arg, final Class<T> target)
			throws IllegalArgumentException, NumberFormatException {
		if (arg == null)
			return null;
		if (target == String.class)
			return (T) arg;

		final Object casted;
		if (target == int.class || target == Integer.class)
			casted = Integer.parseInt(arg);
		else if (target == long.class || target == Long.class)
			casted = Long.parseLong(arg);
		else if (target == double.class || target == Double.class)
			casted = Double.parseDouble(arg);
		else if (target == boolean.class || target == Boolean.class)
			casted = Boolean.parseBoolean(arg);
		else
			throw new IllegalArgumentException("Cannot parse to " + target.getName());
		return (T) casted;
	}

	/**
	 * @return	true if the field is @Mutable and non-final, false otherwise
	 */
	private static boolean isEditable(final Field field) {
		return (field.getAnnotation(Mutable.class) != null) &&
				!Modifier.isFinal(field.getModifiers());
	}
}
