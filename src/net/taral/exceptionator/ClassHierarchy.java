package net.taral.exceptionator;

import java.lang.reflect.Array;
import java.util.HashMap;

public class ClassHierarchy {
	private HashMap<String, String[]> searchMap = new HashMap<String, String[]>();

	public boolean containsKey(Object key) {
		return searchMap.containsKey(key);
	}

	public String[] get(Object key) {
		return searchMap.get(key);
	}

	public String[] put(String key, String[] value) {
		return searchMap.put(key, value);
	}

	public Class<?> getSystemClass(String className) {
		Class<?> c;
		if (className.charAt(0) == '[') {
			c = Array.class;
		} else {
			try {
				c = Class.forName(className.replace('/', '.'), false, getClass().getClassLoader());
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			}
		}
		return c;
	}

	public boolean isSubclass(String c1, String c2) {
		if (c1.equals(c2)) {
			return true;
		}
		String[] search = searchMap.get(c1);
		if (search == null) {
			if (searchMap.containsKey(c2)) {
				return false;
			}
			return getSystemClass(c2).isAssignableFrom(getSystemClass(c1));
		}
		for (String superClass : search) {
			if (isSubclass(superClass, c2)) {
				return true;
			}
		}
		return false;
	}

	public boolean isSubclassOfList(String c1, Iterable<String> c2) {
		for (String c : c2) {
			if (isSubclass(c1, c)) {
				return true;
			}
		}
		return false;
	}

	// Junk exceptions are unchecked exceptions and CloneNotSupportedException.
	public boolean isJunkException(String name) {
		return !name.equals("java/lang/Exception")
				&& isSubclass(name, "java/lang/Exception")
				&& !isSubclass(name, "java/lang/RuntimeException")
				&& !name.equals("java/lang/CloneNotSupportedException");
	}
}
