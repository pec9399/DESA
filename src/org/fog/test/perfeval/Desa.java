package org.fog.test.perfeval;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.Cloud;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.MicroserviceFogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.mobilitydata.References;
import org.fog.placement.Controller;
import org.fog.placement.CustomController;
import org.fog.placement.LocationHandler;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.ModulePlacementOnlyCloud;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.Debug;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.CustomRequest;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;

public class Desa {
	public static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<CustomRequest> sensors = new ArrayList<CustomRequest>();
    static List<Actuator> actuators = new ArrayList<Actuator>();
    static LocationHandler locator;
    public static Application emergencyApp;
    public int appId = -1;
    public static int currentInstances = 0; //only available for HPA since it is centralized
    public static CustomRequest connections, monitorRequest; 
    public static Cloud cloud;
    public static Map<String, Double> totalMips = new HashMap<String,Double>();
	public static boolean hpa = true;
    public static CustomController controller;
    public static ModuleMapping moduleMapping;
    public static int maxInstances = 0;
    public static double RTU = 0.0;
    public static double avgCPU = 0.0;
    public static int monitorCount = 0;
    public static File file = new File("C:\\Users\\WebEng\\Desktop\\debug.csv");
    public static Debug debug;
	public static void main(String args[]) {
		try {
			
			
		
			Log.disable(); 
			Logger.ENABLED = true;
		    int num_user = 1; 
		    Calendar calendar = Calendar.getInstance();
		    boolean trace_flag = false; 
		    CloudSim.init(num_user, calendar, trace_flag);
		    currentInstances = Params.jmin;
		    
		    //cloud
		    cloud = createCloud("cloud", 44800, 40000,100000000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
    		cloud.setParentId(-1);
    		fogDevices.add(cloud);
    		
    		//fogs

    		double latency = 150.0;
    		for(int i = 1; i <= Params.numFogNodes; i++) {
    			FogDevice node = createFogDevice("Node-"+i, 10000, 40000,10000, 10000, 10000, 1, 0.0, 103, 83.25);
    			node.setParentId(cloud.getId());
    			node.setUplinkLatency(latency % 1000); //latency of connection between node and cloud
    			latency+=150.0;
    			fogDevices.add(node);
    			
    		}
		    
    		FogBroker broker1 = new FogBroker("broker-1");
    		Application registry = createGlobalServiceRegistry("registry", broker1.getId());
    		registry.setUserId(broker1.getId());
    			
    		emergencyApp = createMicroservices("emergencyApp", broker1.getId());
    		emergencyApp.setUserId(broker1.getId());
    		
    		Application autoscaler = createAutoscaler("autoscaler", broker1.getId());
    		autoscaler.setUserId(broker1.getId());
    		
    		Application metricServer = createMetricServer("metric-server", broker1.getId());
    		metricServer.setUserId(broker1.getId());
    		
    		CustomRequest monitor = new CustomRequest("monitor","monitor",broker1.getId(),"autoscaler",new DeterministicDistribution(Params.monitorInterval));
    		monitor.setGatewayDeviceId(cloud.getId());
    		sensors.add(monitor);
    
    		CustomRequest requests = new CustomRequest("request","request",broker1.getId(),"registry",new DeterministicDistribution(Params.requestInterval));
    		requests.setGatewayDeviceId(cloud.getId());
    		sensors.add(requests);
    
    		connections = new CustomRequest("connection","connection",broker1.getId(),"emergencyApp",new DeterministicDistribution(0));
    		connections.setGatewayDeviceId(cloud.getId());
    		sensors.add(connections);
    		
    		CustomRequest metric = new CustomRequest("metric","metric",broker1.getId(),"metric-server",new DeterministicDistribution(1000));
    		metric.setGatewayDeviceId(cloud.getId());
    		sensors.add(metric);
    	
    		
    		moduleMapping = ModuleMapping.createModuleMapping();
    		moduleMapping.addModuleToDevice("registry", "cloud");
    		
    		//hpa
    		if(hpa) {
	    		moduleMapping.addModuleToDevice("monitorComponent", "cloud");
	    		moduleMapping.addModuleToDevice("analyzeComponent", "cloud");
	    		moduleMapping.addModuleToDevice("planComponent", "cloud");
	    		moduleMapping.addModuleToDevice("executeComponent", "cloud");
	    		moduleMapping.addModuleToDevice("metric-server", "cloud");
    		} else {
    			
    		}
    		
    		controller = new CustomController("master-controller", fogDevices, sensors,
    				actuators);
    		
    		controller.submitApplication(registry, new ModulePlacementMapping(fogDevices,registry,moduleMapping));
    		controller.submitApplication(autoscaler,  new ModulePlacementMapping(fogDevices,autoscaler,moduleMapping));
    		controller.submitApplication(emergencyApp, new ModulePlacementMapping(fogDevices,emergencyApp, moduleMapping));
    		controller.submitApplication(metricServer, new ModulePlacementMapping(fogDevices,metricServer, moduleMapping));
    		
    		debug = new Debug();
    		debug.addNodes(fogDevices);
    		
    		
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			CloudSim.startSimulation();
			CloudSim.stopSimulation();
			
		} catch (Exception e) {
			e.printStackTrace();
            try {
		        System.in.read();
		      } catch (IOException e1) {e1.printStackTrace(); }
            Log.printLine("Unexpected error has occured");
		}
	    
	}
	
	

    /**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower1
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, int storage, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();
		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
		int hostId = FogUtils.generateEntityId(); // host storage
		int bw = 100000;
		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);
		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.00; // the cost of using memory in this resource
		double costPerStorage = 0.000; // the cost of using storage in this
		double costPerBw = 3.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);
		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics,
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}
	
	private static Cloud createCloud(String nodeName, long mips,
			int ram, int storage, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();
		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
		int hostId = FogUtils.generateEntityId(); // host storage
		int bw = 100000;
		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);
		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.00; // the cost of using memory in this resource
		double costPerStorage = 0.000; // the cost of using storage in this
		double costPerBw = 3.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now
		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);
		Cloud c = null;
		try {
			c = new Cloud(nodeName, characteristics,
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		c.setLevel(level);
		return c;
	}
	
	 
    @SuppressWarnings({"serial"})
  	private static Application createGlobalServiceRegistry(String appId, int userId){
    	Application application = Application.createApplication(appId, userId);
  		ArrayList<String> modules = new ArrayList<String>();
  		application.addAppModule("registry", 10000,10000,10000);
  		modules.add("registry");
  		
  		application.addAppEdge("request", "registry", Params.requestCPULength, Params.requestNetworkLength, "request", Tuple.UP, AppEdge.SENSOR);
  		
  		final AppLoop loop1 = new AppLoop(modules);
  		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
  		application.setLoops(loops);
  		return application;
    }
    
    @SuppressWarnings({"serial"})
  	private static Application createMicroservices(String appId, int userId){
    	Application application = Application.createApplication(appId, userId);
  		ArrayList<String> modules = new ArrayList<String>();
  		
  		application.addAppModule("emergencyApp", 1000,1000,1000);
 
  		
  		application.addAppEdge("connection", "emergencyApp", Params.requestCPULength, Params.requestNetworkLength, "connection", Tuple.UP, AppEdge.SENSOR);
  		
  		final AppLoop loop1 = new AppLoop(modules);
  		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
  		application.setLoops(loops);

  		
  		return application;
    }
    
    @SuppressWarnings({"serial"})
  	private static Application createAutoscaler(String appId, int userId){
    	Application application = Application.createApplication(appId, userId);
  		ArrayList<String> modules = new ArrayList<String>();
  		
  		if(hpa) {
	  		application.addAppModule("monitorComponent", 10,10,10);
	  		modules.add("monitorComponent");
	  		application.addAppModule("analyzeComponent", 10,10,10);
	  		modules.add("analyzeComponent");
	  		application.addAppModule("planComponent", 10,10,10);
	  		modules.add("planComponent");
	  		application.addAppModule("executeComponent", 10,10,10);
	  		modules.add("executeComponent");
  		} else {

  		}
  		application.addAppEdge("monitor", "monitorComponent", Params.requestCPULength, Params.requestNetworkLength, "monitor", Tuple.UP, AppEdge.SENSOR);
  		
  		final AppLoop loop1 = new AppLoop(modules);
  		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
  		application.setLoops(loops);

  		
  		return application;
    }
    
    @SuppressWarnings({"serial"})
  	private static Application createMetricServer(String appId, int userId){
    	Application application = Application.createApplication(appId, userId);
  		ArrayList<String> modules = new ArrayList<String>();
  		
  		if(hpa) {
	  		application.addAppModule("metric-server", 10,10,10);
	  		modules.add("metric-server");
  		} else {

  		}
  		application.addAppEdge("metric", "metric-server", 10, 10, "metric", Tuple.UP, AppEdge.SENSOR);
  		
  		final AppLoop loop1 = new AppLoop(modules);
  		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
  		application.setLoops(loops);

  		
  		return application;
    }
    
}
