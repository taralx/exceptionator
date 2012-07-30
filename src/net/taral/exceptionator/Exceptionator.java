package net.taral.exceptionator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.RemappingClassAdapter;

public class Exceptionator {
	private ClassHierarchy ch = new ClassHierarchy();
	private MethodMap map = new MethodMap(ch);
	public boolean reframe = false;

	public void processFile(File in, File out) throws IOException {
		ZipFile zf = new ZipFile(in);
		ZipFile zfReframed;
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(out));

		if (reframe) {
			/*
			 * Some jars are not compiled for Java 6 (or later), and are thus
			 * missing the analytic data we need to do type analysis. Instead of
			 * doing the analysis on the fly, we use ASM's built-in
			 * reconstruction to get this data.
			 */
			System.out.println("Phase 0: Rewrite with frames.");
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
				zos.putNextEntry(ze);
				zos.write(b);
			}
			zos.close();
			zfReframed = new ZipFile(out);
		} else {
			zfReframed = zf;
		}

		/*
		 * In order to compute the exception set properly, we first need the
		 * class hierarchy. This is used in phase 2 to determine if a try/catch
		 * block covers a throw or not.
		 */
		System.out.println("Phase 1: Read class hierarchy.");
		Phase1ClassVisitor p1 = new Phase1ClassVisitor(ch);
		for (Enumeration<? extends ZipEntry> en = zfReframed.entries(); en.hasMoreElements();) {
			ZipEntry ze = en.nextElement();
			if (ze.getName().endsWith(".class")) {
				InputStream inputStream = zfReframed.getInputStream(ze);
				ClassReader classReader = new ClassReader(inputStream);
				classReader.accept(p1, 0);
			}
		}

		/*
		 * Now that the class hierarchy is available, we can gather the
		 * exception data. This collects calls (with the try/catch blocks in
		 * effect at the time), as well as the uncaught throws.
		 */
		System.out.println("Phase 2: Read exception data.");
		Phase2ClassVisitor p2 = new Phase2ClassVisitor(ch, map);
		for (Enumeration<? extends ZipEntry> en = zfReframed.entries(); en.hasMoreElements();) {
			ZipEntry ze = en.nextElement();
			if (ze.getName().endsWith(".class")) {
				InputStream inputStream = zfReframed.getInputStream(ze);
				ClassReader classReader = new ClassReader(inputStream);
				classReader.accept(p2, ClassReader.EXPAND_FRAMES);
			}
		}
		if (zf != zfReframed) {
			// We don't need the frame data anymore.
			zfReframed.close();
		}

		System.out.println("Phase 3: Propagate exception declarations.");
		/*
		 * Because phase 2 augments the existing throws declarations with the
		 * uncaught throws found in the code, we first have to chase parents to
		 * propagate those exceptions upwards to overridden methods. Otherwise
		 * the parents may never get updated.
		 */
		for (Map.Entry<String, MethodInfo> entry : map.entrySet()) {
			String key = entry.getKey();
			MethodInfo methodInfo = entry.getValue();
			if ((methodInfo.access & Opcodes.ACC_STATIC) == 0) {
				map.updateParents(key, methodInfo.exceptions);
			}
		}

		/*
		 * This is a pretty brute-force algorithm. We iterate over the methods,
		 * updating each one's throws with the throws of its callees. If we
		 * update the throws, we take care to update parents as well.
		 */
		boolean retry = true;
		while (retry) {
			retry = false;
			for (Map.Entry<String, MethodInfo> entry : map.entrySet()) {
				String key = entry.getKey();
				MethodInfo methodInfo = entry.getValue();
				for (MethodCall call : methodInfo.calls) {
					MethodInfo calleeMethodInfo;
					calleeMethodInfo = map.findMethodInfo(call.className, call.methodDesc);
					if (calleeMethodInfo == null) {
						throw new RuntimeException("Method not found");
					}
					if (!calleeMethodInfo.exceptions.isEmpty()) {
						// Make a copy so we can delete any exceptions covered
						// by try/catch blocks.
						HashSet<String> calleeExceptions = new HashSet<String>(calleeMethodInfo.exceptions);
						if (call.tryList != null) {
							Iterator<String> i = calleeExceptions.iterator();
							while (i.hasNext()) {
								String exception = i.next();
								if (ch.isSubclassOfList(exception, Arrays.asList(call.tryList))) {
									i.remove();
								}
							}
						}
						if (methodInfo.exceptions.addAll(calleeExceptions)) {
							retry = true;
							if ((methodInfo.access & Opcodes.ACC_STATIC) == 0) {
								map.updateParents(key, calleeExceptions);
							}
						}
					}
				}
			}
		}

		/*
		 * The above algorithms don't always trim out overlaps. So this O(n^2)
		 * algorithm eliminates any throws declarations that are covered by
		 * another throws declaration on the same method.
		 */
		System.out.println("Phase 4: Generate minimal exception lists.");
		for (Map.Entry<String, MethodInfo> entry : map.entrySet()) {
			MethodInfo methodInfo = entry.getValue();
			HashSet<String> exceptions = methodInfo.exceptions;
			Iterator<String> i = exceptions.iterator();
			while (i.hasNext()) {
				String e = i.next();
				if (!ch.isJunkException(e)) {
					throw new RuntimeException("Exception leakage.");
				}
				for (String e2 : exceptions) {
					if (!e.equals(e2) && ch.isSubclass(e, e2)) {
						i.remove();
						break;
					}
				}
			}
		}

		/*
		 * Finally, we write out the jar file. We take advantage of this to
		 * remap obfuscated class names so that the result is buildable. (Most
		 * notably, "aux.class" won't extract on Windows due to ancient DOS
		 * compatibility.)
		 */
		System.out.println("Phase 5: Write the new jar.");
		zos = new ZipOutputStream(new FileOutputStream(out));
		for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements();) {
			ZipEntry ze = e.nextElement();
			String name = ze.getName();
			if (!name.endsWith(".class"))
				continue;
			InputStream inputStream = zf.getInputStream(ze);
			ClassReader classReader = new ClassReader(inputStream);
			ClassWriter cw = new ClassWriter(0);
			ClassRemapper remapper = new ClassRemapper();
			RemappingClassAdapter rca = new RemappingClassAdapter(cw, remapper);
			classReader.accept(new Phase4ClassVisitor(map, rca), 0);
			byte[] b = cw.toByteArray();
			ze = new ZipEntry(remapper.mapType(name.substring(0, name.length() - 6)) + ".class");
			ze.setSize(b.length);
			zos.putNextEntry(ze);
			zos.write(b);
		}
		zos.close();
		zf.close();

		System.out.println("Operation complete. Thank you for using the Exceptionator!");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Exceptionator exceptionator = new Exceptionator();

		LinkedList<String> argList = new LinkedList<String>();
		argList.addAll(Arrays.asList(args));

		while (true) {
			String flag = argList.peek();
			if (flag.equals("-reframe")) {
				exceptionator.reframe = true;
			} else {
				break;
			}
			argList.remove();
		}

		if (argList.size() != 2) {
			System.err.println("Syntax: Exceptionator [-reframe] <injar> <outjar>");
			System.exit(1);
			return;
		}

		try {
			File inFile = new File(argList.remove());
			File outFile = new File(argList.remove());
			exceptionator.processFile(inFile, outFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
