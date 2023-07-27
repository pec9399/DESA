package org.fog.test.perfeval;

public class Params {
	public static final boolean userLog = false;
	public static final boolean tupleLog = false;
	public static final int numFogNodes = 10;
	public static final int jmax = 20;
	public static final int jmin = 4;
	public static final int maxInstancePerNode = 2;
	public static final int requestCPULength = 1000*1000;
	public static final int requestNetworkLength = 1000*100;
	public static final int monitorInterval = 15000; //15s
	public static final int requestInterval = 1000; //1s
	public static final double requestUtilization=0.1;
	public static final double upperThreshold = 0.5;
}
