package org.fog.entities;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.mobilitydata.Clustering;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
import org.fog.utils.*;
import org.json.simple.JSONObject;

import java.util.*;

public class Cloud extends FogDevice {
    protected Queue<Tuple> northTupleQueue;
    protected Queue<Pair<Tuple, Integer>> southTupleQueue;

    protected List<String> activeApplications;

    protected Map<String, Application> applicationMap;
    protected Map<String, List<String>> appToModulesMap;
    protected Map<Integer, Double> childToLatencyMap;


    protected Map<Integer, Integer> cloudTrafficMap;

    protected double lockTime;

    /**
     * ID of the parent Fog Device
     */
    protected int parentId;

    /**
     * ID of the Controller
     */
    protected int controllerId;
    /**
     * IDs of the children Fog devices
     */
    protected List<Integer> childrenIds;

    protected Map<Integer, List<String>> childToOperatorsMap;

    /**
     * Flag denoting whether the link southwards from this FogDevice is busy
     */
    protected boolean isSouthLinkBusy;

    /**
     * Flag denoting whether the link northwards from this FogDevice is busy
     */
    protected boolean isNorthLinkBusy;

    protected double uplinkBandwidth;
    protected double downlinkBandwidth;
    protected double uplinkLatency;
    protected List<Pair<Integer, Double>> associatedActuatorIds;

    protected double energyConsumption;
    protected double lastUtilizationUpdateTime;
    protected double lastUtilization;
    private int level;

    protected double ratePerMips;

    protected double totalCost;

    protected Map<String, Map<String, Integer>> moduleInstanceCount;

    protected List<Integer> clusterMembers = new ArrayList<Integer>();
    protected boolean isInCluster = false;
    protected boolean selfCluster = false; // IF there is only one fog device in one cluster without any sibling
    protected Map<Integer, Double> clusterMembersToLatencyMap; // latency to other cluster members

    protected Queue<Pair<Tuple, Integer>> clusterTupleQueue;// tuple and destination cluster device ID
    protected boolean isClusterLinkBusy; //Flag denoting whether the link connecting to cluster from this FogDevice is busy
    protected double clusterLinkBandwidth;
    static int cnt = 0;
    
    private double averageCPUUtilization = 0.0;
    private int desiredReplicas = 0;
    
    public Cloud(
            String name,
            FogDeviceCharacteristics characteristics,
            VmAllocationPolicy vmAllocationPolicy,
            List<Storage> storageList,
            double schedulingInterval,
            double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval,uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
        setCharacteristics(characteristics);
        setVmAllocationPolicy(vmAllocationPolicy);
        setLastProcessTime(0.0);
        setStorageList(storageList);
        setVmList(new ArrayList<Vm>());
        setSchedulingInterval(schedulingInterval);
        setUplinkBandwidth(uplinkBandwidth);
        setDownlinkBandwidth(downlinkBandwidth);
        setUplinkLatency(uplinkLatency);
        setRatePerMips(ratePerMips);
        setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
        for (Host host : getCharacteristics().getHostList()) {
            host.setDatacenter(this);
        }
        setActiveApplications(new ArrayList<String>());
        // If this resource doesn't have any PEs then no useful at all
        if (getCharacteristics().getNumberOfPes() == 0) {
            throw new Exception(super.getName()
                    + " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
        }
        // stores id of this class
        getCharacteristics().setId(super.getId());

        applicationMap = new HashMap<String, Application>();
        appToModulesMap = new HashMap<String, List<String>>();
        northTupleQueue = new LinkedList<Tuple>();
        southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
        setNorthLinkBusy(false);
        setSouthLinkBusy(false);


        setChildrenIds(new ArrayList<Integer>());
        setChildToOperatorsMap(new HashMap<Integer, List<String>>());

        this.cloudTrafficMap = new HashMap<Integer, Integer>();

        this.lockTime = 0;

        this.energyConsumption = 0;
        this.lastUtilization = 0;
        setTotalCost(0);
        setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
        setChildToLatencyMap(new HashMap<Integer, Double>());

        clusterTupleQueue = new LinkedList<>();
        setClusterLinkBusy(false);

    }


    protected void updateAllocatedMips(String incomingOperator) {
        getHost().getVmScheduler().deallocatePesForAllVms();
        for (final Vm vm : getHost().getVmList()) {
            if (vm.getCloudletScheduler().runningCloudlets() > 0 || ((AppModule) vm).getName().equals(incomingOperator)) {
                getHost().getVmScheduler().allocatePesForVm(vm, new ArrayList<Double>() {
                    protected static final long serialVersionUID = 1L;

                    {
                        add((double) getHost().getTotalMips());
                    }
                });
            } else {
                getHost().getVmScheduler().allocatePesForVm(vm, new ArrayList<Double>() {
                    protected static final long serialVersionUID = 1L;

                    {
                        add(0.0);
                    }
                });
            }
        }

        updateEnergyConsumption();
        //updateUtilization();
    }

    private void updateUtilization() {
    	for (final Vm vm : getHost().getVmList()) {
            AppModule operator = (AppModule) vm;

            operator.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(operator).getVmScheduler()
                    .getAllocatedMipsForVm(operator));
            //Desa.totalMips.merge(operator.getName(),  getHost().getTotalAllocatedMipsForVm(vm),Double::sum);
            Desa.totalMips.put(operator.getName(),  getHost().getTotalAllocatedMipsForVm(vm));
           
    	}
    }
    
    private void updateEnergyConsumption() {
        double totalMipsAllocated = 0;
        for (final Vm vm : getHost().getVmList()) {
            AppModule operator = (AppModule) vm;
            operator.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(operator).getVmScheduler()
                    .getAllocatedMipsForVm(operator));
            totalMipsAllocated += getHost().getTotalAllocatedMipsForVm(vm);
            Desa.totalMips.merge(operator.getName(),  getHost().getTotalAllocatedMipsForVm(vm),Double::sum);
        }
  
        double timeNow = CloudSim.clock();
        double currentEnergyConsumption = getEnergyConsumption();
        double newEnergyConsumption = currentEnergyConsumption + (timeNow - lastUtilizationUpdateTime) * getHost().getPowerModel().getPower(lastUtilization);
        setEnergyConsumption(newEnergyConsumption);
	
		/*if(getName().equals("d-0")){
			System.out.println("------------------------");
			System.out.println("Utilization = "+lastUtilization);
			System.out.println("Power = "+getHost().getPowerModel().getPower(lastUtilization));
			System.out.println(timeNow-lastUtilizationUpdateTime);
		}*/

        double currentCost = getTotalCost();
        double newcost = currentCost + (timeNow - lastUtilizationUpdateTime) * getRatePerMips() * lastUtilization * getHost().getTotalMips();
        setTotalCost(newcost);

        lastUtilization = Math.min(1, totalMipsAllocated / getHost().getTotalMips());
        lastUtilizationUpdateTime = timeNow;
       // if(lastUtilization > 0.0 && !getName().equals("cloud"))Logger.debug(getName(),""+lastUtilization);
    }

    void monitor() {
    	Logger.debug(getName(),"Start Monitor");
    	averageCPUUtilization = 0.0;
    	for (FogDevice node : Desa.fogDevices) {
    		for (Vm vm : node.getVmList()) {
			   AppModule operator = (AppModule) vm;
	            if(operator.getName().contains("emergencyApp-")) {
	            	double utilization = vm.getCloudletScheduler().getTotalUtilizationOfCpu(lastUtilizationUpdateTime) ;
	            	//Logger.debug(operator.getName(),utilization + "%");
	            	averageCPUUtilization += utilization;
	            }		
    		}
         
    	}
    	averageCPUUtilization /= Desa.currentInstances;
    	Logger.debug(getName(), String.format("Average utilization: %.2f %%", averageCPUUtilization*100));
    	send(getId(), 0, FogEvents.ANALYZE, null);
    }
    
    void analyze() {
    	Logger.debug(getName(),"Start Analyze");
    	//kubernetes hpa algorithm
    	desiredReplicas = (int)Math.ceil(Desa.currentInstances * (averageCPUUtilization/Params.upperThreshold));
    	if(desiredReplicas > Desa.currentInstances) {
    		Logger.debug(getName(),"Scale up to " + desiredReplicas + " instances");
    	}
    	send(getId(), 0, FogEvents.PLAN, null);
    }
    
    void plan() {
    	Logger.debug(getName(),"Start Plan");
    	//round-robin
    	send(getId(), 0, FogEvents.EXECUTE, null);
    }
    
    void execute() {
    	Logger.debug(getName(),"Start Execute");
    }
    
    protected void processTupleArrival(SimEvent ev) {
        Tuple tuple = (Tuple) ev.getData();

        //if (getName().equals("cloud")) {
            updateCloudTraffic();
        //}
		
		/*if(getName().equals("d-0") && tuple.getTupleType().equals("_SENSOR")){
			System.out.println(++numClients);
		}*/
        
        if(tuple.getDestModuleName().equals("registry")) {
        	
        	int numRequest = (int) (tuple.getCloudletLength() / Params.requestCPULength);
        	Logger.debug("registry", "Received " +numRequest + " requests, connect to instances..");
        	int currentInstances = -1;
        	if(!Desa.hpa) {
	        	for(AppModule m : Desa.emergencyApp.getModules()) {
	        		if(m.getName().indexOf('-') == m.getName().lastIndexOf('-'))
	        			currentInstances++;
	        	}
        	} else {
        		currentInstances = Desa.emergencyApp.getModules().size()-1;
        	}
        	for(int i = 1; i <= numRequest; i++) {
        		int cur = (cnt % currentInstances)+1;
	        	Desa.connections.transmit("emergencyApp-"+cur, Desa.emergencyApp.getModuleByName("emergencyApp-"+cur).node.getId(), CloudSim.getMinTimeBetweenEvents());
	        	cnt++;
        	}
        	
        } else if(tuple.getTupleType().equals("monitor")) {
        	int currentInstances = -1;
        	if(!Desa.hpa) {
        		for(AppModule m : Desa.emergencyApp.getModules()) {
	        		if(m.getName().indexOf('-') == m.getName().lastIndexOf('-'))
	        			currentInstances++;
	        	}
        	} else {
        		currentInstances = Desa.emergencyApp.getModules().size()-1;
        	}
        	Logger.debug(getName(),"Start MAPE");
        	//hpa case
        	if(Desa.hpa) {
        		monitor();
        	//desa case
        	} else {
	        	for(int i = 1; i <= currentInstances; i++) {
	        		Desa.connections.transmit("emergencyApp-"+i+"-monitor", Desa.emergencyApp.getModuleByName("emergencyApp-"+i+"-monitor").node.getId(), 0);
	        	}
        	}
        }

        	
    	//Logger.debug(getName(), "Received tuple " + tuple.getCloudletId() + " with tupleType = " + tuple.getTupleType() + "\t| Source : " +
    	//	CloudSim.getEntityName(ev.getSource()) + "|Dest : " + CloudSim.getEntityName(ev.getDestination()));
    	
        send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);

        if (tuple.getDirection() == Tuple.ACTUATOR) {
            sendTupleToActuator(tuple);
            return;
        }

        if (getHost().getVmList().size() > 0) {
            final AppModule operator = (AppModule) getHost().getVmList().get(0);
            if (CloudSim.clock() > 0) {
                getHost().getVmScheduler().deallocatePesForVm(operator);
                getHost().getVmScheduler().allocatePesForVm(operator, new ArrayList<Double>() {
                    protected static final long serialVersionUID = 1L;

                    {
                        add((double) getHost().getTotalMips());
                    }
                });
            }
        }


        if (tuple.getDestModuleName() == null) {
            sendNow(getControllerId(), FogEvents.TUPLE_FINISHED, null);
        }

        if (appToModulesMap.containsKey(tuple.getAppId())) {
            if (appToModulesMap.get(tuple.getAppId()).contains(tuple.getDestModuleName())) {
                int vmId = -1;
                for (Vm vm : getHost().getVmList()) {
                    if (((AppModule) vm).getName().equals(tuple.getDestModuleName()))
                        vmId = vm.getId();
                }
                if (vmId < 0
                        || (tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) &&
                        tuple.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId)) {
                    return;
                }
                tuple.setVmId(vmId);
                //Logger.error(getName(), "Executing tuple for operator " + moduleName);

                updateTimingsOnReceipt(tuple);

                executeTuple(ev, tuple.getDestModuleName());
            } else if (tuple.getDestModuleName() != null) {
                if (tuple.getDirection() == Tuple.UP)
                    sendUp(tuple);
                else if (tuple.getDirection() == Tuple.DOWN) {
                    for (int childId : getChildrenIds())
                        sendDown(tuple, childId);
                }
            } else {
                sendUp(tuple);
            }
        } else {
            if (tuple.getDirection() == Tuple.UP)
                sendUp(tuple);
            else if (tuple.getDirection() == Tuple.DOWN) {
                for (int childId : getChildrenIds())
                    sendDown(tuple, childId);
            }
        }
        
    }

    protected void updateTimingsOnReceipt(Tuple tuple) {
        Application app = getApplicationMap().get(tuple.getAppId());
        String srcModule = tuple.getSrcModuleName();
        String destModule = tuple.getDestModuleName();
        List<AppLoop> loops = app.getLoops();
        for (AppLoop loop : loops) {
            if (loop.hasEdge(srcModule, destModule) && loop.isEndModule(destModule)) {
                Double startTime = TimeKeeper.getInstance().getEmitTimes().get(tuple.getActualTupleId());
                if (startTime == null)
                    break;
                if (!TimeKeeper.getInstance().getLoopIdToCurrentAverage().containsKey(loop.getLoopId())) {
                    TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loop.getLoopId(), 0.0);
                    TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loop.getLoopId(), 0);
                }
                double currentAverage = TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loop.getLoopId());
                int currentCount = TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loop.getLoopId());
                double delay = CloudSim.clock() - TimeKeeper.getInstance().getEmitTimes().get(tuple.getActualTupleId());
                TimeKeeper.getInstance().getEmitTimes().remove(tuple.getActualTupleId());
                double newAverage = (currentAverage * currentCount + delay) / (currentCount + 1);
                TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loop.getLoopId(), newAverage);
                TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loop.getLoopId(), currentCount + 1);
                break;
            }
        }
    }


    protected void executeTuple(SimEvent ev, String moduleName) {
        if(moduleName.equals("registry"))
        		Logger.debug(getName(), "Executing tuple on module " + moduleName);
        Tuple tuple = (Tuple) ev.getData();

        AppModule module = getModuleByName(moduleName);

        if (tuple.getDirection() == Tuple.UP) {
            String srcModule = tuple.getSrcModuleName();
            if (!module.getDownInstanceIdsMaps().containsKey(srcModule))
                module.getDownInstanceIdsMaps().put(srcModule, new ArrayList<Integer>());
            if (!module.getDownInstanceIdsMaps().get(srcModule).contains(tuple.getSourceModuleId()))
                module.getDownInstanceIdsMaps().get(srcModule).add(tuple.getSourceModuleId());

            int instances = -1;
            for (String _moduleName : module.getDownInstanceIdsMaps().keySet()) {
                instances = Math.max(module.getDownInstanceIdsMaps().get(_moduleName).size(), instances);
            }
            module.setNumInstances(instances);
        }

        TimeKeeper.getInstance().tupleStartedExecution(tuple);
        updateAllocatedMips(moduleName);
        processCloudletSubmit(ev, false);
        updateAllocatedMips(moduleName);
		/*for(Vm vm : getHost().getVmList()){
			Logger.error(getName(), "MIPS allocated to "+((AppModule)vm).getName()+" = "+getHost().getTotalAllocatedMipsForVm(vm));
		}*/
        
    	/*for (final Vm vm : getHost().getVmList()) {
            AppModule operator = (AppModule) vm;
            if(operator.getName().contains("emergencyApp-")) {
            	Logger.debug(operator.getName(),""+vm.getCloudletScheduler().getTotalUtilizationOfCpu(lastUtilizationUpdateTime)*100 + "%");
            }
    	}*/
    }


}