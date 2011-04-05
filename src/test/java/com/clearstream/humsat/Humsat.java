package com.clearstream.humsat;

import org.apache.log4j.Logger;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * @TITLE System Test
 * The system tests consists of the configuration of a simple virtual satellite, Humsat, and the
 * execution of all components forming part of the Humsat control system. The system tests thus act 
 * as validation as well as inspiration in how to configure a mission.
 * 
 * For the test to run, ActiveMQ must be available on the local server, on the default port 61616. 
 * @END
 * 
 * */
public class Humsat {	
	
	protected static Logger logger = Logger.getLogger(Humsat.class);
	/**
	 * Main method for starting the simulator.
	 * 
	 * @param args The path to the Spring assembly file. Must be in the format 'classpath:[relative path]' or 'file:[full path]'.
	 */
	public static void main(String[] args) {
		
		/** Read the configuration file as the first argument. If not set, then we try the default name. */
		String assemblyFile = System.getProperty("humsat.assembly") == null ? "classpath:Humsat.xml" : System.getProperty("humsat.assembly");
		logger.info("Starting Hummingbird based on assembly file '" + assemblyFile + "'.");

		new FileSystemXmlApplicationContext(assemblyFile);
		
		while(true) {
			try {
				Thread.sleep(5000);
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			logger.info("Humsat is Alive!");
		}
	}
}
