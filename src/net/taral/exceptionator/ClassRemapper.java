package net.taral.exceptionator;

import org.objectweb.asm.commons.Remapper;

public class ClassRemapper extends Remapper {
	@Override
	public String map(String typeName) {
		int pos = typeName.lastIndexOf('/');
		if (pos == -1) {
			return "net/minecraft/src/C_" + typeName;
		}
		String base = typeName.substring(pos + 1);
		char first = base.charAt(0);
		if (base.length() <= 3 && first >= 'a' && first <= 'z') {
			return typeName.substring(0, pos + 1) + "C_" + base;
		}
		return typeName;
	}
}
