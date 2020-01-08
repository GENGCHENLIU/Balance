package core;

/** Task types are loaded through this classloader. */
public final class TaskLoader extends ClassLoader {
	public static final TaskLoader INSTANCE = new TaskLoader();

	/**
	 * @throws ClassFormatError	If the data did not contain a valid class
	 * @throws SecurityException	If an attempt is made to add this class to a
	 * package that contains classes that were signed by a different set of
	 * certificates than this class (which is unsigned), or if name begins with "java."
	 * @see ClassLoader#defineClass(String, byte[], int, int)
	 */
	public Class<?> define(final byte[] data)
			throws ClassFormatError, SecurityException {
		return super.defineClass(null, data, 0, data.length);
	}
}
