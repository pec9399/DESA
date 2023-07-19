package org.fog.test.perfeval;

public class Params {
	public static final boolean userLog = true;
	public static final boolean tupleLog = false;
	public static final int numFogNodes = 10;
	public static final int jmax = 20;
	public static final int jmin = 4;
	public static final int maxInstancePerNode = 2;
	public static final int requestCPULength = 10;
	public static final int requestNetworkLength = 10;
	public static final int monitorInterval = 1100; //15s
	public static final double requestUtilization=0.2;
	public static final double upperThreshold = 0.5;
}