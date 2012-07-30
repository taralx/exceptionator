package net.taral.exceptionator;

import java.util.ArrayList;
import java.util.HashSet;

public class MethodInfo {
	HashSet<String> exceptions = new HashSet<String>();
	ArrayList<MethodCall> calls = new ArrayList<MethodCall>();
	int access;
	
	public MethodInfo(int access) {
		this.access = access;
	}
}