package ui;

import com.cedarsoftware.util.io.JsonReader;
import core.Task;
import core.TaskLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Class in default package for easier command line invocation.
 */
public final class Balance {
	private static final String CONFIG_FILE = "balance.properties";

	static final Properties DEFAULT_CONFIG = new Properties();
	static {
		DEFAULT_CONFIG.setProperty("task_types_dir", "task-types.d");
		DEFAULT_CONFIG.setProperty("save_dir", "save.d");
		// auto save interval, in integer seconds; negative for no auto save
		DEFAULT_CONFIG.setProperty("auto_save_int", "300");
	}

	static final core.Balance BALANCE;
	static {
		// load config
		final var configPath = Path.of(CONFIG_FILE);
		var config = new Properties(DEFAULT_CONFIG);
		try { config.load(Files.newBufferedReader(configPath)); }
		catch (Exception e) {
			error("Failed to load config file: " + configPath.toAbsolutePath(),
					e, false);
			config = new Properties(DEFAULT_CONFIG);
		}

		BALANCE = new core.Balance(config);
	}


	public static void main(final String... args) {
		final var config = BALANCE.getConfig();

		// load Task class types
		try {
			Files.walk(Path.of(config.getProperty("task_types_dir")))
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".class"))
					.forEach(path -> {
						try { Task.loadAndRegister(path); }
						catch (IOException e) {
							System.err.println("Failed to read file: " + path);
						}
						catch (ClassFormatError e) {
							System.err.println("Malformatted class file: " + path);
						}
						catch (ClassCastException e) {
							System.err.println("Bad Task type definition: " + path);
						}
						catch (Exception e) {
							error("Task type definition failed to load: " + path, e, false);
						}
					});
		}
		catch (Exception e) {
			error("Failed to load Task types", e, true);
		}

		// load save
		try {
			final var saveDirStr = config.getProperty("save_dir");
			final var saveDir = Path.of(saveDirStr);
			for (final var it = Files.list(saveDir).iterator(); it.hasNext(); ) {
				final var file = it.next();
				try {
					// JsonReader wants a mutable map
					final var jsonArgs = new HashMap<String, Object>(Map.of(
							"CLASSLOADER", TaskLoader.INSTANCE
					));
					final var task = JsonReader.jsonToJava(Files.readString(file), jsonArgs);
					BALANCE.add((Task) task);
				}
				catch (Exception e) {
					error("Failed to load save: " + file, e, false);
				}
			}
		}
		catch (Exception e) {
			error("Failed to load save", e, true);
		}

		// start auto save
		int saveInterval;
		try { saveInterval = Integer.parseInt(config.getProperty("auto_save_int")); }
		catch (Exception e) {
			error("Illegal value for property 'auto_save_int': " + config.getProperty("auto_save_int"),
					e, false);
			saveInterval = Integer.parseInt(DEFAULT_CONFIG.getProperty("auto_save_int"));
		}

		if (saveInterval > 0) {
			final int finalSaveInterval = saveInterval;
			final var autoSave = new Thread(() -> {
				while (!Thread.currentThread().isInterrupted()) {
					try { Thread.sleep(1000 * finalSaveInterval); }
					catch (InterruptedException e) { Thread.currentThread().interrupt(); }
					Command.SAVE.execute();
				}
			}, "Auto Save");
			autoSave.setDaemon(true);
			autoSave.start();
		}

		// start main loop
		final var stdin = new BufferedReader(new InputStreamReader(System.in));
		do { System.err.print("> "); }
		while ( handleCommand(parseCommand(readLine(stdin))) );

		// save on exit
		Command.SAVE.execute();
	}

	private static String readLine(final BufferedReader reader) {
		try { return reader.readLine(); }
		catch (IOException e) { throw new RuntimeException(e); }
	}


	static void error(final String msg, final Throwable t, final boolean sameLine) {
		/*
		if sameLine
		msg: t's class name[: t's message if not empty]
		if not sameLine
		msg
		t's class name[: t's message if not empty]
		 */
		System.err.print(msg);
		if (sameLine) System.err.print(": ");
		else System.err.println();
		System.err.print('\t' + t.getClass().getName());

		final var exceptionMsg = t.getMessage();
		if (exceptionMsg != null && !exceptionMsg.isEmpty())
			System.err.print(": " + exceptionMsg);
		System.err.println();
	}

	/**
	 * @param cmd   parsed command arguments
	 * @return false to indicate exit to the calling code, true otherwise.
	 * @see #parseCommand(String)
	 */
	private static boolean handleCommand(final List<String> cmd) {
		if (cmd == null) return false;  // end of stream, exit
		if (cmd.size() == 0) return true;

		final var cmdObj = Command.valueOfCmd(cmd.get(0));
		if (cmdObj == null) {   // command not found
			System.err.println("Unrecognized command: " + cmd.get(0));
			Command.HELP.execute();
			return true;
		}

		return cmdObj.execute(BALANCE, cmd.subList(1, cmd.size()));
	}


	/**
	 * Parse the full command string to parts, handling quotes and escapes.<br>
	 * Double quoted strings are interpreted as raw string. The backslash will always
	 * escape the next character.
	 */
	private static List<String> parseCommand(final String cmd) {
		if (cmd == null) return null;   // end of stream

		final var parts = new ArrayList<String>(5);
		final var buffer = new StringBuilder();

		boolean inQuote = false;
		boolean escaped = false;

		for (int i = 0; i < cmd.length(); i++) {
			final char c = cmd.charAt(i);

			if (escaped) {
				buffer.append(c);
				escaped = false;
				continue;
			}

			if (c == '\\') {
				escaped = true;
				continue;
			}

			// if unquoted whitespace and buffer isn't empty
			if ((c == ' ' || c == '\t') && !inQuote && buffer.length() != 0) {
				parts.add(buffer.toString());
				buffer.delete(0, buffer.length());
				continue;
			}

			if (c == '"' || c == '\'') {
				inQuote = !inQuote;
				continue;
			}

			buffer.append(c);
		}
		if (buffer.length() != 0)
			parts.add(buffer.toString());

		return parts;
	}
}
