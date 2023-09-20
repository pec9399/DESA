package org.fog.test.perfeval;

import java.util.Random;

public class Params {
	public static final boolean userLog = false;
	public static final boolean tupleLog = false;
	public static boolean external = false;
	public static int numFogNodes = 1000;
	public static final int jmax = 20;
	public static int jmin = 4;
	public static final int maxInstancePerNode = 2;
	public static int requestCPULength = 200 * numFogNodes;
	public static int requestNetworkLength = 200* numFogNodes;
	public static final int podMips = 5;
	public static final int podRam = 5;
	public static final int podSize = 100;

	public static final int monitorInterval = 15000; //15s
	public static final int requestInterval = 1000; //1s
	public static final double upperThreshold = 0.5;
	public static boolean hpa = false;
	public static int seed = 2;
	public static int burstTime = (int)(new Random(seed).nextInt(60*1000, 5*60*1000));
	public static int burstDuration = 5*60*1000;
	public static int numBurstUser = 1000;
	public static boolean fromFile = false;

}
