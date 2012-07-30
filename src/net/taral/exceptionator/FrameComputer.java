package net.taral.exceptionator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class SuperclassClassVisitor extends ClassVisitor {
	private String superClass;

	private SuperclassClassVisitor() {
		super(Opcodes.ASM4);
	}
	
	@Override
	public void visit(int version, int access, String name, String signature,
			String superName, String[] interfaces) {
		superClass = superName;
	}

	public static String getSuperClass(InputStream is) throws IOException {
		ClassReader cr = new ClassReader(is);
		SuperclassClassVisitor cv = new SuperclassClassVisitor();
		cr.accept(cv, 0);
		return cv.superClass;
	}
}

public class FrameComputer {

	private void processFile(File i, File o) throws IOException {
		ZipFile zf = new ZipFile(i);
		JarOutputStream jf = new JarOutputStream(new FileOutputStream(o));
		try {
			for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
				ZipEntry ze = e.nextElement();
				if (!ze.getName().endsWith(".class"))
					continue;
				InputStream inputStream = zf.getInputStream(ze);
				ClassReader classReader = new ClassReader(inputStream);
				ClassWriter cw = new ClassRewriter(zf, ClassWriter.COMPUTE_FRAMES);
				classReader.accept(cw, ClassReader.SKIP_FRAMES);
				byte[] b = cw.toByteArray();
				ze.setSize(b.length);
				ze.setCompressedSize(-1);
				jf.putNextEntry(ze);
				jf.write(b);
			}
		} finally {
			jf.close();
			zf.close();
		}
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length != 2) {
			System.err.println("Syntax: FrameComputer <injar> <outjar>");
			System.exit(1);
			return;
		}

		try {
			new FrameComputer().processFile(new File(args[0]), new File(args[1]));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
