package net.taral.exceptionator;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class Phase1ClassVisitor extends ClassVisitor {
	private final ClassHierarchy ch;

	public Phase1ClassVisitor(ClassHierarchy ch) {
		super(Opcodes.ASM4);
		this.ch = ch;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		String[] a = new String[interfaces.length + 1];
		a[0] = superName;
		System.arraycopy(interfaces, 0, a, 1, interfaces.length);
		ch.put(name, a);
	}
}
