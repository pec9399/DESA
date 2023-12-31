package org.fog.utils.distribution;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.entities.Cloud;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.mobilitydata.References;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;

public class CustomRequest extends Sensor{
	private Queue<Integer> requestQueue;
	public static int numUsers = 10;
	/**
	 * This constructor is called from the code that generates PhysicalTopology from JSON
	 * @param name
	 * @param tupleType
	 * @param string 
	 * @param userId
	 * @param appId
	 * @param transmitDistribution
	 */
	public CustomRequest(String name, String tupleType, int userId, String appId, Distribution transmitDistribution) {
		super(name, tupleType, userId, appId, transmitDistribution);
		requestQueue = new LinkedList<Integer>();
	}
	
	@Override
	public void startEntity() {
		send(gatewayDeviceId, CloudSim.getMinTimeBetweenEvents(), FogEvents.SENSOR_JOINED, geoLocation);
		if(appId.equals("registry") && Params.fromFile) {
			try {
				//read dataset file to generate user requests for case study
				File requests = new File(References.dataset_request);
				Scanner scanner = new Scanner(requests);
				while(scanner.hasNextLine()) {
					String line = scanner.nextLine();
					requestQueue.add(Integer.parseInt(line)/100); //merge requests by 100 to reduce # of objects created
				}
				scanner.close();
			} catch(Exception e) {
				e.printStackTrace();
				System.out.println("Failed to fetch request dataset");
				System.exit(0); 
			}
		}
		if(!appId.equals("emergencyApp"))
			send(getId(), getTransmitDistribution().getNextValue() + transmissionStartDelay, FogEvents.EMIT_TUPLE);
		
	}
	
	@Override
	public void processEvent(SimEvent ev) {
		switch(ev.getTag()){
		case FogEvents.TUPLE_ACK:
			//transmit(transmitDistribution.getNextValue());
			break;
		case FogEvents.EMIT_TUPLE:
			if(!requestQueue.isEmpty())
				transmit(requestQueue.poll());
			else if(!appId.equals("registry")){
				
				transmit(1);
			}else if(appId.equals("registry") && !Params.fromFile) {
				transmit(numUsers);
			}
			if(appId.equals("autoscaler")) {
				send(getId(), getTransmitDistribution().getNextValue()+Cloud.latency, FogEvents.EMIT_TUPLE);
			}else {
			send(getId(), getTransmitDistribution().getNextValue(), FogEvents.EMIT_TUPLE);
			}
			break;
		}		
	}
	
	public void transmit(int numRequest){
			if(numRequest <= 0) {
				Logger.debug("system", "Request end, stop simulation");
				CloudSim.stopSimulation();
				Desa.controller.printDesaResult();
				System.exit(0);
			}
			AppEdge _edge = null;
			for(AppEdge edge : getApp().getEdges()){
				if(edge.getSource().equals(getTupleType()))
					_edge = edge;
			}
			long cpuLength = (long) _edge.getTupleCpuLength()*numRequest;
			long nwLength = (long) _edge.getTupleNwLength()*numRequest;
			
			Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1, nwLength, outputSize, 
					new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
			tuple.setUserId(getUserId());
			tuple.setTupleType(getTupleType());
			
			tuple.setDestModuleName(_edge.getDestination());
			tuple.setSrcModuleName(getSensorName());
			//Logger.debug(getName(), "Sending tuple with tupleId = "+tuple.getCloudletId());
			/*if(appId.equals("registry"))
				Logger.debug(getName(), "Sending "+numRequest + " requests to registry");
			else if(getName().equals("monitor")){
				Logger.debug(getName(), "Fetch metric value : "+tuple.getCloudletId());
			}*/
			tuple.setDestinationDeviceId(getGatewayDeviceId());
	
			int actualTupleId = updateTimings(getSensorName(), tuple.getDestModuleName());
			tuple.setActualTupleId(actualTupleId);
			
			send(gatewayDeviceId, appId.equals("registry")?0:getLatency(), FogEvents.TUPLE_ARRIVAL,tuple);
		
	}
	
	public void transmit(String dest, int nodeId, double latency){
		AppEdge _edge = null;
		for(AppEdge edge : getApp().getEdges()){
			if(edge.getSource().equals(getTupleType()))
				_edge = edge;
		}
		
		if(_edge == null) {
			getApp().addAppEdge("connection", dest, Params.requestCPULength, Params.requestNetworkLength, "connection", Tuple.UP, AppEdge.SENSOR);
			for(AppEdge edge : getApp().getEdges()){
				if(edge.getSource().equals(getTupleType()))
					_edge = edge;
			}
		}

		long cpuLength =  Params.requestCPULength;
		long nwLength =  Params.requestNetworkLength;
		
		Tuple tuple = new Tuple(getAppId(), FogUtils.generateTupleId(), Tuple.UP, cpuLength, 1, nwLength, outputSize, 
				new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
		tuple.setUserId(getUserId());
		tuple.setTupleType(getTupleType());
		
		tuple.setDestModuleName(dest);
		tuple.setSrcModuleName(getSensorName());
		
		tuple.setDestinationDeviceId(nodeId);

		int actualTupleId = updateTimings(getSensorName(), tuple.getDestModuleName());
		tuple.setActualTupleId(actualTupleId);
		
		send(nodeId, getLatency()+latency, FogEvents.TUPLE_ARRIVAL,tuple);
	}


}
