package org.fog.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.FogDevice;
import org.fog.scheduler.TupleScheduler;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;

public class ModulePlacementMapping extends ModulePlacement{

	private ModuleMapping moduleMapping;
	private static int cnt = 0;
	@Override
	protected void mapModules() {
		Map<String, List<String>> mapping = moduleMapping.getModuleMapping();
		for(String deviceName : mapping.keySet()){
			FogDevice device = getDeviceByName(deviceName);
			for(String moduleName : mapping.get(deviceName)){
				
				AppModule module = getApplication().getModuleByName(moduleName);
				if(module == null)
					continue;
				createModuleInstanceOnDevice(module, device);
				//getModuleInstanceCountMap().get(device.getId()).put(moduleName, mapping.get(deviceName).get(moduleName));
			}
		}
		
		//handle unmapped modules
		List<AppModule> modules = getApplication().getModules();
		int s = modules.size();
		for(int j = 0; j < s; j++) {
			AppModule instance = modules.get(j);
			if(instance.getAppId().equals("emergencyApp") && !instance.placed) {
				for(int i = 0; i < Params.jmin;i++) {
					//round-robin
					int cur = (cnt % Params.numFogNodes)+1;
					AppModule module = new AppModule(FogUtils.generateEntityId(), instance.getName()+"-"+cur, instance.getAppId(), instance.getUserId(),
							instance.getMips(), instance.getRam(), instance.getBw(), instance.getSize(), instance.getVmm(), new TupleScheduler(instance.getMips(), 1), new HashMap<Pair<String, String>, SelectivityModel>());
					
					FogDevice node = getDeviceByName("Node-"+cur);
					module.node = node;
					createModuleInstanceOnDevice(module,node);
					Desa.emergencyApp.getModules().add(module);

					Map<Integer, List<AppModule>> deviceToModuleMap = getDeviceToModuleMap();
					for(Integer deviceId : deviceToModuleMap.keySet()){
						sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
					}
					
			  		final AppLoop loop1 = new AppLoop(Desa.emergencyApp.getModuleNames());
			  		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
			  		Desa.emergencyApp.setLoops(loops);
			
					
					cnt++;			 
				}
				
			}
		}
	}
	
	protected void sendNow(int entityId, int cloudSimTag, Object data) {
		send(entityId, 0, cloudSimTag, data);
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
			//Log.printLine(getName() + ".send(): Error - " + "invalid entity id " + entityId);
			return;
		}

		int srcId = Desa.cloud.getId();
		if (entityId != srcId) {// does not delay self messages
			//delay += getNetworkDelay(srcId, entityId);
		}

		schedule(entityId, delay, cloudSimTag, data);
	}
	
	public void schedule(int dest, double delay, int tag, Object data) {
		if (!CloudSim.running()) {
			return;
		}
		CloudSim.send(Desa.cloud.getId(), dest, delay, tag, data);
	}

	

	public ModulePlacementMapping(List<FogDevice> fogDevices, Application application, 
			ModuleMapping moduleMapping){
		this.setFogDevices(fogDevices);
		this.setApplication(application);
		this.setModuleMapping(moduleMapping);
		this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
		this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
		this.setModuleInstanceCountMap(new HashMap<Integer, Map<String, Integer>>());
		for(FogDevice device : getFogDevices()) {
			getModuleInstanceCountMap().put(device.getId(), new HashMap<String, Integer>());
			
		}
		mapModules();
	}
	
	
	public ModuleMapping getModuleMapping() {
		return moduleMapping;
	}
	public void setModuleMapping(ModuleMapping moduleMapping) {
		this.moduleMapping = moduleMapping;
	}

	
}
