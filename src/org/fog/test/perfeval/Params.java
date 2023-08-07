package org.fog.test.perfeval;

public class Params {
	public static final boolean userLog = false;
	public static final boolean tupleLog = false;
	public static boolean external = false;
	public static int numFogNodes = 100;
	public static final int jmax = 20;
	public static int jmin = 4;
	public static final int maxInstancePerNode = 2;
	public static final int requestCPULength = 10000*100;
	public static final int requestNetworkLength = 10*100;
	public static final int podMips = 100;
	public static final int podRam = 100;
	public static final int podSize = 100;

	public static final int monitorInterval = 15000; //15s
	public static final int requestInterval = 1000; //1s
	public static final double requestUtilization=0.1;
	public static final double upperThreshold = 0.5;
	public static boolean hpa = true;
}
