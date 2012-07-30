package net.taral.exceptionator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassWriter;

public class ClassRewriter extends ClassWriter {
	private ZipFile zf;
	private HashMap<String, List<String>> classChainMap = new HashMap<String, List<String>>();

	public ClassRewriter(ZipFile zf, int flags) {
		super(flags);
		this.zf = zf;
	}
	
	private List<String> getClassChainDirect(String type) throws IOException {
		ZipEntry ze = zf.getEntry(type + ".class");
		if (ze == null) {
			ArrayList<String> r = new ArrayList<String>(1);
			r.add(type);
			return r;
		}
		String superClass = SuperclassClassVisitor.getSuperClass(zf.getInputStream(ze));
		List<String> classChain = getClassChainDirect(superClass);
		classChain.add(type);
		return classChain;
	}

	private List<String> getClassChain(String type) throws IOException {
		List<String> classChain = classChainMap.get(type);
		if (classChain == null) {
			classChainMap.put(type, classChain = getClassChainDirect(type));
		}
		return classChain;
	}

	@Override
	protected String getCommonSuperClass(String type1, String type2) {
		if (type1 == type2) {
			return type1;
		}
		
		try {
			List<String> super1 = getClassChain(type1);
			List<String> super2 = getClassChain(type2);
			
			int i = 0;
			while (i < super1.size() && i < super2.size() && super1.get(i) == super2.get(i)) {
				i++;
			}
			if (i == 0) {
				return super.getCommonSuperClass(super1.get(0), super2.get(0));
			}
			return super1.get(i - 1);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}	
}