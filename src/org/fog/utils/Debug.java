package org.fog.utils;


import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.entities.FogDevice;
import org.fog.test.perfeval.Desa;
import org.fog.test.perfeval.Params;
 

public class Debug extends JFrame{
		HashMap<String, JPanel> deviceMap = new HashMap<String, JPanel>();
		HashMap<String, List<String>> deviceToModuleMap = new HashMap<String, List<String>>();
		HashMap<String, JProgressBar> moduleToUtilMap = new HashMap<String, JProgressBar>();
		Font deviceNameFont = new Font("Raleway", Font.BOLD, 15);
		Font moduleNameFont = new Font("Raleway", Font.PLAIN, 12);
		JLabel time,time2, avg, instance, rtu, avgDB;
		JPanel timer, util;
		JFrame stat;
		
		public Debug() {
			stat = new JFrame();
			stat.setSize(500,300);
			stat.setResizable(true);
			stat.setVisible(!Params.external);
			stat.setTitle("Statistics");
			stat.setLayout(new FlowLayout());
			
	        setSize(500, 500); //크기 설정
	        setResizable(true);
	        //GridLayout gridLayout = new GridLayout(Params.numFogNodes/2, Params.numFogNodes/2);
	        setLayout(new FlowLayout());
	        
	        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        setTitle("Realtime monitor");
	      
	        addTimer();
	        addUtil();

	        setVisible(!Params.external);
	        
		}	
		
		public void addTimer() {
			 //timer
	    	timer = new JPanel();
	    	timer.setPreferredSize(new Dimension(200,100));
	    	timer.setBorder(BorderFactory.createLineBorder(Color.black));
	    	timer.setLayout(new FlowLayout());
			
			JLabel label = new JLabel("Simulation Time");
			label.setHorizontalAlignment(JLabel.CENTER);
			label.setPreferredSize(new Dimension(200,15));
			label.setFont(deviceNameFont);
			timer.add(label);
			
			
			time = new JLabel(String.format("%.2f",CloudSim.clock()));
			time.setHorizontalAlignment(JLabel.CENTER);
			time.setFont(moduleNameFont);
			timer.add(time);
			

			time2 = new JLabel(String.format("%.2f",(CloudSim.clock() - Params.burstTime)/1000));
			time2.setHorizontalAlignment(JLabel.CENTER);
			time2.setFont(moduleNameFont);
			timer.add(time2);
			
			timer.setVisible(true);
			
			stat.add(timer);
		}
		
		
		public void addUtil() {
			 //timer
	    	util = new JPanel();
	    	util.setPreferredSize(new Dimension(200,170));
	    	util.setBorder(BorderFactory.createLineBorder(Color.black));
	    	util.setLayout(new FlowLayout());
			
			JLabel label = new JLabel("Avg Utilization");
			label.setHorizontalAlignment(JLabel.CENTER);
			label.setPreferredSize(new Dimension(200,15));
			label.setFont(deviceNameFont);
			util.add(label);
			
			avg = new JLabel("");
			avg.setHorizontalAlignment(JLabel.CENTER);
			avg.setFont(moduleNameFont);
			avg.setPreferredSize(new Dimension(190,15));
			
			avgDB = new JLabel("");
			avgDB.setHorizontalAlignment(JLabel.CENTER);
			avgDB.setFont(moduleNameFont);
			avgDB.setPreferredSize(new Dimension(190,15));
			
			instance = new JLabel("");
			instance.setHorizontalAlignment(JLabel.CENTER);
			instance.setFont(moduleNameFont);
			instance.setPreferredSize(new Dimension(190,15));
			
			rtu = new JLabel("");
			rtu.setHorizontalAlignment(JLabel.CENTER);
			rtu.setFont(moduleNameFont);
			rtu.setPreferredSize(new Dimension(190,15));
			
			
			util.add(avg);
			util.add(avgDB);
			util.add(instance);
			util.add(rtu);
			
			avg.setVisible(true);
			instance.setVisible(true);
			rtu.setVisible(true);
			setInstance(Params.jmin);
			stat.add(util);
		}
		
		public void addNodes(List<FogDevice> l) {
			for(FogDevice d : l) {
		
				JPanel p = new JPanel();
				
				p.setSize(150,150);
				p.setPreferredSize(new Dimension(140,300));

		
				p.setBorder(BorderFactory.createLineBorder(Color.black));
				p.setLayout(new FlowLayout());
				
				JLabel label = new JLabel(d.getName());
				label.setPreferredSize(new Dimension(130,15));
				label.setFont(deviceNameFont);
				p.add(label);
				p.setVisible(true);

				
				JScrollPane scrollFrame = new JScrollPane(p);
				scrollFrame.setPreferredSize(new Dimension(160,150));
				p.setAutoscrolls(true);
				add(scrollFrame);
				deviceMap.put(d.getName(), p);
				deviceToModuleMap.put(d.getName(), new ArrayList<String>());
			}
			
			revalidate();
			repaint();
		}
		
		public void addModule(AppModule m) {
			String deviceName = m.getHost().getDatacenter().getName();
			JPanel p = deviceMap.get(deviceName);
			
			String moduleDisplayName = m.getName();
			moduleDisplayName = moduleDisplayName.replace("Component", "");
			moduleDisplayName = moduleDisplayName.replace("emergencyApp", "Pod");
			
			if(p != null) {
				if(deviceToModuleMap.get(deviceName).contains(moduleDisplayName)) {
					return;
				}
				
				JPanel module = new JPanel();
				JLabel label = new JLabel(moduleDisplayName);
				label.setFont(moduleNameFont);
				module.setBorder(BorderFactory.createLineBorder(Color.black));;
				module.setVisible(true);
				module.add(label);
				
				JProgressBar pb = new JProgressBar(0,100);
				pb.setPreferredSize(new Dimension(50,15));
		        pb.setVisible(true);
		        pb.setValue(0);
		        pb.setStringPainted(true);
		        module.add(pb);
		    
		        
		        //pack();
				
				p.add(module);
				
				p.revalidate();
				p.repaint();
				
				deviceToModuleMap.get(deviceName).add(moduleDisplayName);
				moduleToUtilMap.put(moduleDisplayName, pb);
			}
		}
		
		public void updateUtilization(AppModule m, double utilization) {
			String moduleDisplayName = m.getName();
			moduleDisplayName = moduleDisplayName.replace("Component", "");
			moduleDisplayName = moduleDisplayName.replace("emergencyApp", "Pod");
			JProgressBar pb = moduleToUtilMap.get(moduleDisplayName);
			pb.setValue((int)utilization);
			
		}
		
		public void setTime(double t) {
			time.setText(String.format("%.2f",t));
			time2.setText(String.format("%.2f",(CloudSim.clock() - Params.burstTime)/1000));
			timer.revalidate();
			timer.repaint();
		}
		
		public void setAvgUtil(double t) {

			avg.setText(String.format("%.2f",t)+"%");
			util.revalidate();
			util.repaint();
		}
		
		public void setAvgUtilDuringBurst() {
			avgDB.setText(String.format("%.2f",Desa.avgCPUDuringBurst/Desa.avgCPUcnt)+"%");
			util.revalidate();
			util.repaint();
		}
		public void setInstance(int i) {
			instance.setText("Current instances: "+i);
			util.revalidate();
			util.repaint();
		}
		public void markInstance(int i) {
			if(Params.fromFile) {
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(Desa.file,true))) {
				writer.write(""+CloudSim.clock()/1000.0+",");
				writer.write(""+i+",");
				writer.write("\n");
			} catch (IOException e) {
			    e.printStackTrace();
			    
			    
			}
			}
		}
		
		public void setRTU() {
			rtu.setText("RTU: " + Desa.RTU);
			util.revalidate();
			util.repaint();
			
		}
}


