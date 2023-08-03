package org.fog.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.FogDevice;
import org.fog.entities.Tuple;
import org.fog.scheduler.TupleScheduler;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.distribution.CustomRequest;
import org.fog.utils.distribution.DeterministicDistribution;

public class ModulePlacementMapping extends ModulePlacement{

	private ModuleMapping moduleMapping;
	public int cnt = 0;
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
		
		updateModules(Desa.currentInstances);
	}
	

	public void updateModules(int n) {
		//handle unmapped modules
				Desa.maxInstances = (int)Math.max(Desa.maxInstances, n);
				List<AppModule> modules = getApplication().getModules();
				int s = modules.size();
				for(int j = 0; j < s; j++) {
					AppModule instance = modules.get(j);
					if(instance.getName().equals("emergencyApp") && !instance.placed) {
						//Desa.emergencyApp.setModules(new ArrayList<AppModule>());
						for(int i = 0; i < n;i++) {
							//round-robin
							
							
							int cur = (cnt % Params.numFogNodes)+1;
							if(getApplication().getModuleByName(instance.getName()+"-"+(cnt+1)) != null) {
								cnt++;
								continue;
							}
							AppModule module = new AppModule(FogUtils.generateEntityId(), instance.getName()+"-"+(cnt+1), instance.getAppId(), instance.getUserId(),
									instance.getMips(), instance.getRam(), instance.getBw(), instance.getSize(), instance.getVmm(), new TupleScheduler(instance.getMips(), 1), new HashMap<Pair<String, String>, SelectivityModel>());
							
							String monitorComponent = instance.getName()+"-"+(cnt+1)+"-monitor";							
							Desa.emergencyApp.addAppEdge(monitorComponent, monitorComponent, Params.requestCPULength, Params.requestNetworkLength, Desa.emergencyApp.getAppId(), Tuple.UP, AppEdge.SENSOR);
							FogDevice node = getDeviceByName("Node-"+cur);
							module.node = node;

							
							
							createModuleInstanceOnDevice(module,node);
							Desa.emergencyApp.getModules().add(module);

							AppModule monitor;
							if(!Desa.hpa) {
								 monitor = new AppModule(FogUtils.generateEntityId(), instance.getName()+"-"+(cnt+1)+"-monitor", instance.getAppId(), instance.getUserId(),
										10, 10, instance.getBw(),10, instance.getVmm(), new TupleScheduler(instance.getMips(), 1), new HashMap<Pair<String, String>, SelectivityModel>());
								 monitor.node = node;
								
								 createModuleInstanceOnDevice(monitor, node);
							
								 Desa.emergencyApp.getModules().add(monitor);
							}	
							
							Map<Integer, List<AppModule>> deviceToModuleMap = getDeviceToModuleMap();
							//for(Integer deviceId : deviceToModuleMap.keySet()){
								//sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
							//}
							
					  		final AppLoop loop1 = new AppLoop(Desa.emergencyApp.getModuleNames());
					  		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
					  		Desa.emergencyApp.setLoops(loops);
					  		
							
							cnt++;			 
						}
						break;
					}
				}
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
