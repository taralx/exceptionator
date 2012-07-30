package net.taral.exceptionator;

import java.util.ArrayList;
import java.util.HashMap;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AnalyzerAdapter;

public class Phase2ClassVisitor extends ClassVisitor {
	private String currentClass;
	private final ClassHierarchy ch;
	private final MethodMap map;

	private class Phase2MethodVisitor extends AnalyzerAdapter {
		private String key;
		private ArrayList<String> tryList;
		private HashMap<Label, ArrayList<String>> tryStarts, tryEnds;
		private MethodInfo methodInfo;

		public Phase2MethodVisitor(int access, String name, String desc, String[] exceptions) {
			super(Opcodes.ASM4, currentClass, access, name, desc, null);
			key = currentClass + "." + name + desc;
			methodInfo = map.add(key, access);

			if (exceptions != null && exceptions.length != 0) {
				for (String exception : exceptions) {
					if (!ch.isJunkException(exception)) {
						throw new RuntimeException("Junk exception in throws clause.");
					}
					methodInfo.exceptions.add(exception);
				}
			}
			tryList = new ArrayList<String>();
			tryStarts = new HashMap<Label, ArrayList<String>>();
			tryEnds = new HashMap<Label, ArrayList<String>>();
		}

		@Override
		public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
			super.visitTryCatchBlock(start, end, handler, type);
			if (type == null) {
				// finally block
				return;
			}
			ArrayList<String> labelStarts = tryStarts.get(start);
			if (labelStarts == null) {
				tryStarts.put(start, labelStarts = new ArrayList<String>());
			}
			labelStarts.add(type);
			ArrayList<String> labelEnds = tryEnds.get(end);
			if (labelEnds == null) {
				tryEnds.put(end, labelEnds = new ArrayList<String>());
			}
			labelEnds.add(type);
		}

		@Override
		public void visitLabel(Label label) {
			super.visitLabel(label);
			ArrayList<String> labelEnds = tryEnds.get(label);
			if (labelEnds != null) {
				for (String labelEnd : labelEnds) {
					tryList.remove(labelEnd);
				}
			}
			ArrayList<String> labelStarts = tryStarts.get(label);
			if (labelStarts != null) {
				tryList.addAll(labelStarts);
			}
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String desc, Handle bsm, Object... bsmArgs) {
			throw new RuntimeException("Cannot handle invokedynamic.");
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			super.visitMethodInsn(opcode, owner, name, desc);
			MethodCall methodCall = new MethodCall();
			methodCall.className = owner;
			methodCall.methodDesc = name + desc;
			if (!tryList.isEmpty()) {
				methodCall.tryList = tryList.toArray(new String[0]);
			}
			methodInfo.calls.add(methodCall);
		}

		@Override
		public void visitInsn(int opcode) {
			if (opcode == Opcodes.ATHROW) {
				if (stack == null || stack.size() < 1) {
					throw new RuntimeException("Missing or invalid frame data, please rerun with -reframe.");
				}
				Object exceptionType = stack.get(stack.size() - 1);
				if (exceptionType instanceof String) {
					String exception = (String) exceptionType;
					if (ch.isJunkException(exception) && !ch.isSubclassOfList(exception, tryList)) {
						methodInfo.exceptions.add(exception);
					}
				} else {
					throw new RuntimeException("Internal error.");
				}
			}
			super.visitInsn(opcode);
		}
	}

	public Phase2ClassVisitor(ClassHierarchy ch, MethodMap map) {
		super(Opcodes.ASM4);
		this.ch = ch;
		this.map = map;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		currentClass = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		return new Phase2MethodVisitor(access, name, desc, exceptions);
	}
}
