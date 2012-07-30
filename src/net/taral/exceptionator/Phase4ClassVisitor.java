package net.taral.exceptionator;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class Phase4ClassVisitor extends ClassVisitor {
	private String currentClass;
	private MethodMap map;

	public Phase4ClassVisitor(MethodMap map, ClassVisitor cv) {
		super(Opcodes.ASM4, cv);
		this.map = map;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		currentClass = name;
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		String key = currentClass + "." + name + desc;
		MethodInfo mi = map.get(key);
		if (mi != null) {
			exceptions = mi.exceptions.toArray(new String[0]);
		}
		return super.visitMethod(access, name, desc, signature, exceptions);
	}
}