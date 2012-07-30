package net.taral.exceptionator;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class DescriptorConverter {
	public static Class<?>[] getParametersForDescriptor(String descriptor, ClassLoader cl) throws ClassNotFoundException {
		if (descriptor.charAt(0) != '(') {
			throw new RuntimeException("Not a method descriptor: " + descriptor);
		}
		ArrayList<Class<?>> r = new ArrayList<Class<?>>();
		int i = 1;
		int arrayCount = 0;
		while (i < descriptor.length()) {
			Class<?> c;
			switch (descriptor.charAt(i)) {
			case ')':
				return r.toArray(new Class<?>[0]);
			case '[':
				arrayCount += 1;
				i += 1;
				continue;
			case 'I':
				c = int.class;
				break;
			case 'V':
				c = void.class;
				break;
			case 'Z':
				c = boolean.class;
				break;
			case 'B':
				c = byte.class;
				break;
			case 'C':
				c = char.class;
				break;
			case 'S':
				c = short.class;
				break;
			case 'D':
				c = double.class;
				break;
			case 'F':
				c = float.class;
				break;
			case 'J':
				c = long.class;
				break;
			case 'L':
				int end = descriptor.indexOf(';', i + 1);
				if (end == -1) {
					throw new RuntimeException("Unterminated L: " + descriptor);
				}
				c = Class.forName(descriptor.substring(i + 1, end).replace('/', '.'), false, cl);
				i = end;
				break;
			default:
				throw new RuntimeException("Illegal character in descriptor: " + descriptor);
			}
			while (arrayCount > 0) {
				c = Array.newInstance(c, 0).getClass();
				arrayCount -= 1;
			}
			r.add(c);
			i += 1;
		}
		throw new RuntimeException("Unexpected end of descriptor: " + descriptor);
	}
}
