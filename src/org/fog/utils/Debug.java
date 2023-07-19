package org.fog.utils;


import java.awt.FlowLayout;
 
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
 

public class Debug extends JFrame{
	public static JProgressBar progress; 
		public Debug() {
	        setSize(300, 200); //크기 설정
	        setLayout(new FlowLayout());	        
	        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	        setTitle("Realtime monitor");
	        
	    	int minimum = 0;
	        int maximum = 100;
	        progress = new JProgressBar(minimum, maximum);
	        add(progress);
	      
	        setVisible(true);
	
	        progress.setValue(0);
	        progress.setStringPainted(true);
	        progress.setVisible(true);
	    }
	    
	 
}
