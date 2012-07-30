package net.taral.exceptionator;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.Opcodes;

public class MethodMap {
	private final ClassHierarchy ch;
	private final HashMap<String, MethodInfo> methodMap = new HashMap<String, MethodInfo>();
	private static final HashMap<String, MethodInfo> synthesisCache = new HashMap<String, MethodInfo>();

	public MethodMap(ClassHierarchy ch) {
		this.ch = ch;
	}
	
	public MethodInfo get(String key) {
		return methodMap.get(key);
	}
	
	public Set<Entry<String, MethodInfo>> entrySet() {
		return methodMap.entrySet();
	}

	public MethodInfo add(String key, int access) {
		MethodInfo mi = new MethodInfo(access);
		methodMap.put(key, mi);
		return mi;
	}
	
	public void updateParents(String key, Collection<String> calleeExceptions) {
		int dot = key.indexOf('.');
		String className = key.substring(0, dot);
		String methodDesc = key.substring(dot + 1);

		String[] search = ch.get(className);
		if (search == null) {
			return;
		}
		for (String superClass : search) {
			String superKey = superClass + "." + methodDesc;
			MethodInfo mi = methodMap.get(superKey);
			boolean propagate;
			if (mi == null) {
				propagate = true;
			} else {
				// private methods aren't visible to subclasses
				// TODO Handle package visibility?
				if ((mi.access & Opcodes.ACC_PRIVATE) != 0) {
					continue;
				}
				propagate = mi.exceptions.addAll(calleeExceptions);
			}
			if (propagate) {
				updateParents(superKey, calleeExceptions);
			}
		}
	}

	public MethodInfo findMethodInfo(String className, String methodDesc) {
		if (!ch.containsKey(className)) {
			return synthesizeMethodInfo(className, methodDesc);
		}
		String key = className + "." + methodDesc;
		MethodInfo methodInfo = methodMap.get(key);
		if (methodInfo != null) {
			return methodInfo;
		}
		for (String superClass : ch.get(className)) {
			methodInfo = findMethodInfo(superClass, methodDesc);
			if (methodInfo != null) {
				return methodInfo;
			}
		}
		return null;
	}

	public MethodInfo synthesizeMethodInfo(String className, String methodDesc) {
		String key = className + "." + methodDesc;
		MethodInfo mi = synthesisCache.get(key);
		if (mi != null) {
			return mi;
		}

		Class<?> c = ch.getSystemClass(className);
		int paren = methodDesc.indexOf('(');
		String methodName = methodDesc.substring(0, paren);
		String descriptor = methodDesc.substring(paren);
		Class<?>[] parameterTypes;
		try {
			parameterTypes = DescriptorConverter.getParametersForDescriptor(descriptor, getClass().getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		mi = synthesizeMethodInfo(c, methodName, parameterTypes);
		synthesisCache.put(key, mi);
		return mi;
	}

	public MethodInfo synthesizeMethodInfo(Class<?> c, String methodName, Class<?>[] parameterTypes) {
		Class<?>[] exceptionTypes;
		int access;
		try {
			if (methodName.equals("<init>")) {
				Constructor<?> constructor = c.getDeclaredConstructor(parameterTypes);
				exceptionTypes = constructor.getExceptionTypes();
				access = constructor.getModifiers();
			} else {
				Method method = c.getDeclaredMethod(methodName, parameterTypes);
				exceptionTypes = method.getExceptionTypes();
				access = method.getModifiers();
			}
		} catch (NoSuchMethodException e) {
			Class<?> sc = c.getSuperclass();
			if (sc != null) {
				MethodInfo mi = synthesizeMethodInfo(sc, methodName, parameterTypes);
				if (mi != null) {
					return mi;
				}
			}
			for (Class<?> ic : c.getInterfaces()) {
				MethodInfo mi = synthesizeMethodInfo(ic, methodName, parameterTypes);
				if (mi != null) {
					return mi;
				}
			}
			return null;
		}
		MethodInfo mi = new MethodInfo(access);
		for (Class<?> e : exceptionTypes) {
			String name = e.getName().replace('.', '/');
			if (ch.isJunkException(name)) {
				mi.exceptions.add(name);
			}
		}
		return mi;
	}
}