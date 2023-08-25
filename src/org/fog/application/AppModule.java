package org.fog.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerVm;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.FogDevice;
import org.fog.placement.ModulePlacementMapping;
import org.fog.scheduler.TupleScheduler;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;

/**
 * Class representing an application module, the processing elements of the application model of iFogSim.
 * @author Harshit Gupta
 *
 */
public class AppModule extends PowerVm{

	private String name;
	private String appId;
	private Map<Pair<String, String>, SelectivityModel> selectivityMap;
	
	/**
	 * A map from the AppModules sending tuples UP to this module to their instance IDs.
	 * If a new instance ID is detected, the number of instances is incremented.  
	 */
	private Map<String, List<Integer>> downInstanceIdsMaps;
	
	/**
	 * Number of instances of this module
	 */
	private int numInstances;
	
	/**
	 * Mapping from tupleType emitted by this AppModule to Actuators subscribing to that tupleType
	 */
	private Map<String, List<Integer>> actuatorSubscriptions;
	public boolean placed = false;
	public FogDevice node = null;
    private double averageCPUUtilization = 0.0;
    private int desiredReplicas = 0;
    private int childNum = 0;
    
	public AppModule(
			int id,
			String name,
			String appId,
			int userId,
			double mips,
			int ram,
			long bw,
			long size,
			String vmm,
			CloudletScheduler cloudletScheduler,
			Map<Pair<String, String>, SelectivityModel> selectivityMap) {
		super(id, userId, mips, 1, ram, bw, size, 1, vmm, cloudletScheduler, 300);
		setName(name);
		setId(id);
		setAppId(appId);
		setUserId(userId);
		setUid(getUid(userId, id));
		setMips(mips);
		setNumberOfPes(1);
		setRam(ram);
		setBw(bw);
		setSize(size);
		setVmm(vmm);
		setCloudletScheduler(cloudletScheduler);
		setInMigration(false);
		setBeingInstantiated(true);
		setCurrentAllocatedBw(0);
		setCurrentAllocatedMips(null);
		setCurrentAllocatedRam(0);
		setCurrentAllocatedSize(0);
		setSelectivityMap(selectivityMap);
		setActuatorSubscriptions(new HashMap<String, List<Integer>>());
		setNumInstances(0);
		setDownInstanceIdsMaps(new HashMap<String, List<Integer>>());
	}
	public AppModule(AppModule operator) {
		super(FogUtils.generateEntityId(), operator.getUserId(), operator.getMips(), 1, operator.getRam(), operator.getBw(), operator.getSize(), 1, operator.getVmm(), new TupleScheduler(operator.getMips(), 1), operator.getSchedulingInterval());
		setName(operator.getName());
		setAppId(operator.getAppId());
		setInMigration(false);
		setBeingInstantiated(true);
		setCurrentAllocatedBw(0);
		setCurrentAllocatedMips(null);
		setCurrentAllocatedRam(0);
		setCurrentAllocatedSize(0);
		setSelectivityMap(operator.getSelectivityMap());
		setDownInstanceIdsMaps(new HashMap<String, List<Integer>>());
	}
	
	public void subscribeActuator(int id, String tuplyType){
		if(!getActuatorSubscriptions().containsKey(tuplyType))
			getActuatorSubscriptions().put(tuplyType, new ArrayList<Integer>());
		getActuatorSubscriptions().get(tuplyType).add(id);
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public Map<Pair<String, String>, SelectivityModel> getSelectivityMap() {
		return selectivityMap;
	}
	public void setSelectivityMap(Map<Pair<String, String>, SelectivityModel> selectivityMap) {
		this.selectivityMap = selectivityMap;
	}
	public String getAppId() {
		return appId;
	}
	public void setAppId(String appId) {
		this.appId = appId;
	}
	public Map<String, List<Integer>> getActuatorSubscriptions() {
		return actuatorSubscriptions;
	}
	public void setActuatorSubscriptions(Map<String, List<Integer>> actuatorSubscriptions) {
		this.actuatorSubscriptions = actuatorSubscriptions;
	}
	public Map<String, List<Integer>> getDownInstanceIdsMaps() {
		return downInstanceIdsMaps;
	}
	public void setDownInstanceIdsMaps(Map<String, List<Integer>> downInstanceIdsMaps) {
		this.downInstanceIdsMaps = downInstanceIdsMaps;
	}
	public int getNumInstances() {
		return numInstances;
	}
	public void setNumInstances(int numInstances) {
		this.numInstances = numInstances;
	}
	
	
	//DESA's MAPE
    public void monitor(double utilization) {
    	
         averageCPUUtilization = utilization;
         handledMips = 0.0;
         CloudSim.lastResetTime = CloudSim.clock();
         Logger.debug(getName(), String.format("%.2f%%",averageCPUUtilization));
         send(node.getId(),Params.numFogNodes+ Desa.currentInstances*3,FogEvents.ANALYZE, this);

    }
    public boolean analyze() {
		    	//Logger.debug(getName(),"Start Analyze");

		    		if(childNum == 0) {
		    			desiredReplicas = (int)Math.ceil((averageCPUUtilization/(Params.upperThreshold*100)));
		    			childNum+= desiredReplicas;
		    		} else {
		    			desiredReplicas = (int)Math.floor((averageCPUUtilization/(Params.upperThreshold*100)));
		    		}
		    		
		
		    	
		    	if(desiredReplicas > 1)
		    		return plan();
		    
		    	return false;
		    }
		    
    public  boolean plan() {
		    	//Logger.debug(getName(),"Start Plan");
		    	return execute();
		    }
		    
    public  boolean execute() {
    			
		    	Logger.debug(getName(),"Scale " + (desiredReplicas-1) + " instances");
		    	Desa.currentInstances += desiredReplicas-1;
				Desa.maxInstances = Math.max(Desa.currentInstances, Desa.maxInstances);
				Desa.controller.submitApplication(Desa.emergencyApp,(int)node.getUplinkLatency(), new ModulePlacementMapping(Desa.fogDevices,Desa.emergencyApp, Desa.moduleMapping));
				
				Desa.debug.setInstance(Desa.currentInstances);
				node.send(Desa.cloud.getId(),5,FogEvents.SCALEUP);
				
				for(String appId : Desa.controller.applications.keySet()){
					if(appId.equals("emergencyApp")) {
						//send(getId(), (int)node.getUplinkLatency(), FogEvents.APP_SUBMIT, Desa.controller.applications.get(appId));
						Desa.controller.processAppSubmit((int)node.getUplinkLatency()+Params.numFogNodes, Desa.controller.applications.get(appId));
						return true;
					}
				}
				return true;
		    	//Logger.debug(getName(),"Start Execute");
		    }
	protected void send(int entityId, double delay, int cloudSimTag, Object data) {
		if (entityId < 0) {
			return;
		}

		// if delay is -ve, then it doesn't make sense. So resets to 0.0
		if (delay < 0) {
			delay = 0;
		}

		if (Double.isInfinite(delay)) {
			throw new IllegalArgumentException("The specified delay is infinite value");
		}

		if (entityId < 0) {
			Log.printLine(getName() + ".send(): Error - " + "invalid entity id " + entityId);
			return;
		}

		int srcId = getId();

		schedule(entityId, delay, cloudSimTag, data);
	}
	public void schedule(int dest, double delay, int tag, Object data) {
		if (!CloudSim.running()) {
			return;
		}
		CloudSim.send(getId(), dest, delay, tag, data);
	}

}
