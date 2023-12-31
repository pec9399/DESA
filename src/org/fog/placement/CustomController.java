package org.fog.placement;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.CustomRequest;

public class CustomController extends SimEntity{
	
	public static boolean ONLY_CLOUD = false;
		
	private List<FogDevice> fogDevices;
	public List<Sensor> sensors;
	private List<Actuator> actuators;
	
	public Map<String, Application> applications;
	public Map<String, Integer> appLaunchDelays;

	private Map<String, ModulePlacement> appModulePlacementPolicy;

	
	public CustomController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators) {
		super(name);
		this.applications = new HashMap<String, Application>();
		setAppLaunchDelays(new HashMap<String, Integer>());
		setAppModulePlacementPolicy(new HashMap<String, ModulePlacement>());
		for(FogDevice fogDevice : fogDevices){
			fogDevice.setControllerId(getId());
		}
		setFogDevices(fogDevices);
		setActuators(actuators);
		setSensors(sensors);
		connectWithLatencies();
	}

	private FogDevice getFogDeviceById(int id){
		for(FogDevice fogDevice : getFogDevices()){
			if(id==fogDevice.getId())
				return fogDevice;
		}
		return null;
	}
	
	private void connectWithLatencies(){
		for(FogDevice fogDevice : getFogDevices()){
			FogDevice parent = getFogDeviceById(fogDevice.getParentId());
			if(parent == null)
				continue;
			double latency = fogDevice.getUplinkLatency();
			parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
			parent.getChildrenIds().add(fogDevice.getId());
		}
	}
	
	@Override
	public void startEntity() {
		for(String appId : applications.keySet()){
			if(getAppLaunchDelays().get(appId)==0)
				processAppSubmit(applications.get(appId));
			else
				send(getId(), getAppLaunchDelays().get(appId), FogEvents.APP_SUBMIT, applications.get(appId));
		}

		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
		if(!Params.fromFile) {
			send(getId(),Params.burstTime,FogEvents.BURST);
			send(getId(),Params.burstTime+Params.burstDuration,FogEvents.BURSTEND);
			send(getId(),Params.burstTime+Params.burstDuration+1000,FogEvents.STOP_SIMULATION);
		}
		//send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);
		
		for(FogDevice dev : getFogDevices())
			sendNow(dev.getId(), FogEvents.RESOURCE_MGMT);

	}

	@Override
	public void processEvent(SimEvent ev) {
		switch(ev.getTag()){
		case FogEvents.APP_SUBMIT:
			processAppSubmit(ev);
			break;
		case FogEvents.TUPLE_FINISHED:
			processTupleFinished(ev);
			break;
		case FogEvents.CONTROLLER_RESOURCE_MANAGE:
			manageResources();
			break;
		case FogEvents.BURST:
			burst();
			break;
		case FogEvents.BURSTEND:
			burstEnd();
			break;
		case FogEvents.STOP_SIMULATION:
			CloudSim.stopSimulation();
			//printTimeDetails();
			//printPowerDetails();
			//printCostDetails();
		    //printNetworkUsageDetails();
			printDesaResult();
			System.exit(0);
			break;
		case FogEvents.SCALEUP:
			processAppSubmit((Application)(ev.getData()));
			break;
		}
	}
	
	public void burst() {
		
		Logger.debug("system", "burst start");
		CustomRequest.numUsers = 1000;
	}
	
	public void burstEnd() {
		Logger.debug("system", "burst end");
		
		
		
		CustomRequest.numUsers = 10;
	}
	
	public void printDesaResult() {
		if(Params.external) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(Desa.file,true))) {
				writer.write(Desa.hpa ?"hpa,":"desa,");
				
				writer.write(""+Params.jmin+",");
				writer.write(""+Params.numFogNodes+",");
			
				writer.write(""+(Desa.RTU == -1? Desa.lastRTU : Desa.RTU)+",");
				writer.write(""+Desa.maxInstances+",");
				writer.write(""+Desa.avgCPUDuringBurst/Desa.avgCPUcnt+",");
				writer.write(""+Desa.finalUtilization+",");
				writer.write(""+Params.seed+",");
				writer.write("\n");
			} catch (IOException e) {
			    e.printStackTrace();
			    
			    
			}
			
			printMergedResult();
			
		} else {
			System.out.println("RTU: "+(Desa.RTU == -1? Desa.lastRTU : Desa.RTU)+",");
			System.out.println("max(j(t)): " + Desa.maxInstances);
			System.out.println("avgCPU during burst "+Desa.avgCPUDuringBurst/Desa.avgCPUcnt+",");
			System.out.println("Final utilization "+Desa.finalUtilization+",");
		}
	}
	
	public void printMergedResult() {

		try (BufferedWriter writer = new BufferedWriter(new FileWriter(Desa.file2,true))) {
			writer.write(Desa.hpa ?"hpa,":"desa,");
			
			writer.write(""+Params.jmin+",");
			writer.write(""+Params.numFogNodes+",");
			writer.write(""+(Desa.RTU == -1? Desa.lastRTU : Desa.RTU)+",");
			writer.write(""+Desa.maxInstances+",");
			writer.write(""+Desa.avgCPUDuringBurst/Desa.avgCPUcnt+",");
			writer.write(""+Desa.finalUtilization+",");
			writer.write(""+Params.seed+",");
			writer.write("\n");
		} catch (IOException e) {
		    e.printStackTrace();
		    try {
				wait(1000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		    printMergedResult();
		    
		}
	}
	
	
	private void printNetworkUsageDetails() {
		System.out.println("Total network usage = "+NetworkUsageMonitor.getNetworkUsage()/Config.MAX_SIMULATION_TIME);		
	}

	private FogDevice getCloud(){
		for(FogDevice dev : getFogDevices())
			if(dev.getName().equals("cloud"))
				return dev;
		return null;
	}
	
	private void printCostDetails(){
		System.out.println("Cost of execution in cloud = "+getCloud().getTotalCost());
	}
	
	private void printPowerDetails() {
		for(FogDevice fogDevice : getFogDevices()){
			System.out.println(fogDevice.getName() + " : Energy Consumed = "+fogDevice.getEnergyConsumption());
		}
	}

	private String getStringForLoopId(int loopId){
		for(String appId : getApplications().keySet()){
			Application app = getApplications().get(appId);
			for(AppLoop loop : app.getLoops()){
				if(loop.getLoopId() == loopId)
					return loop.getModules().toString();
			}
		}
		return null;
	}
	private void printTimeDetails() {
		System.out.println("=========================================");
		System.out.println("============== RESULTS ==================");
		System.out.println("=========================================");
		System.out.println("EXECUTION TIME : "+ (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
		System.out.println("=========================================");
		System.out.println("APPLICATION LOOP DELAYS");
		System.out.println("=========================================");
		for(Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()){
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
			System.out.println(getStringForLoopId(loopId) + " ---> "+TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId));
		}
		System.out.println("=========================================");
		System.out.println("TUPLE CPU EXECUTION DELAY");
		System.out.println("=========================================");
		
		for(String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()){
			System.out.println(tupleType + " ---> "+TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
		}
		
		System.out.println("=========================================");
	}

	protected void manageResources(){
		send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
	}
	
	private void processTupleFinished(SimEvent ev) {
	}
	
	@Override
	public void shutdownEntity() {	
	}
	
	public void submitApplication(Application application, int delay, ModulePlacement modulePlacement){
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		getAppLaunchDelays().put(application.getAppId(), delay);
		getAppModulePlacementPolicy().put(application.getAppId(), modulePlacement);
		
		for(Sensor sensor : sensors){
			sensor.setApp(getApplications().get(sensor.getAppId()));
		}
		for(Actuator ac : actuators){
			ac.setApp(getApplications().get(ac.getAppId()));
		}
		
	
	}
	
	public void submitApplication(Application application, ModulePlacement modulePlacement){
		submitApplication(application, 0, modulePlacement);
	}
	
	
	private void processAppSubmit(SimEvent ev){
		Application app = (Application) ev.getData();
		processAppSubmit(app);
	}
	
	int del = 0;
	public void processAppSubmit(Application application){
		Logger.debug(getName(),CloudSim.clock()+" Submitted application "+ application.getAppId());
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		
		ModulePlacement modulePlacement = getAppModulePlacementPolicy().get(application.getAppId());
	
		//for(FogDevice fogDevice : fogDevices){
			//send(fogDevice.getId(),0, FogEvents.ACTIVE_APP_UPDATE, application);
		//}
		
		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		//int cnt = 0;
		for(Integer deviceId : deviceToModuleMap.keySet()){
			for(AppModule module : deviceToModuleMap.get(deviceId)){
				send(deviceId, 0,FogEvents.APP_SUBMIT, application);
				send(deviceId, 0,FogEvents.LAUNCH_MODULE, module);
				//cnt++;
			}
		}
		del++;
		//System.out.println(cnt);
	}
	
	public void processAppSubmit(double latency, Application application){
		Logger.debug(getName(),(CloudSim.clock()+latency)+" Submitted application "+ application.getAppId());
		FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
		getApplications().put(application.getAppId(), application);
		
		ModulePlacement modulePlacement = getAppModulePlacementPolicy().get(application.getAppId());
	
		//for(FogDevice fogDevice : fogDevices){
			//send(fogDevice.getId(),0, FogEvents.ACTIVE_APP_UPDATE, application);
		//}
		
		Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
		//int cnt = 0;
		for(Integer deviceId : deviceToModuleMap.keySet()){
			for(AppModule module : deviceToModuleMap.get(deviceId)){
				send(deviceId, latency,FogEvents.APP_SUBMIT, application);
				send(deviceId, latency,FogEvents.LAUNCH_MODULE, module);
				//cnt++;
			}
		}
		del++;
		//System.out.println(cnt);
	}

	public List<FogDevice> getFogDevices() {
		return fogDevices;
	} 

	public void setFogDevices(List<FogDevice> fogDevices) {
		this.fogDevices = fogDevices;
	}

	public Map<String, Integer> getAppLaunchDelays() {
		return appLaunchDelays;
	}

	public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
		this.appLaunchDelays = appLaunchDelays;
	}

	public Map<String, Application> getApplications() {
		return applications;
	}

	public void setApplications(Map<String, Application> applications) {
		this.applications = applications;
	}

	public List<Sensor> getSensors() {
		return sensors;
	}

	public void setSensors(List<Sensor> sensors) {
		for(Sensor sensor : sensors)
			sensor.setControllerId(getId());
		this.sensors = sensors;
	}

	public List<Actuator> getActuators() {
		return actuators;
	}

	public void setActuators(List<Actuator> actuators) {
		this.actuators = actuators;
	}

	public Map<String, ModulePlacement> getAppModulePlacementPolicy() {
		return appModulePlacementPolicy;
	}

	public void setAppModulePlacementPolicy(Map<String, ModulePlacement> appModulePlacementPolicy) {
		this.appModulePlacementPolicy = appModulePlacementPolicy;
	}
}