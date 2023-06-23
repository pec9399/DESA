package org.fog.placement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.entities.FogDevice;
import org.fog.scheduler.TupleScheduler;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
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
				
					cnt++;			 
				}
				
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
