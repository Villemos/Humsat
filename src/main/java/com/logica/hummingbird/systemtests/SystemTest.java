package com.logica.hummingbird.systemtests;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit38.AbstractJUnit38SpringContextTests;


/**
 * @TITLE System Test
 * The system tests consists of the configuration of a simple virtual satellite, Humsat, and the
 * execution of all components. The system tests thus act as validation as well as inspiration in
 * how to configure a mission.
 * 
 * For the test to run, ActiveMQ must be available on the local server. 
 * @END
 * 
 * */
@ContextConfiguration
public class SystemTest extends AbstractJUnit38SpringContextTests {	
	
	public void testInstances() throws Exception {
		/** All contexts should be running by now, i.e. the system is processing, injecting
		 * data into the standard topics below. We only need to implement the actual checks and
		 * then exist at some point. */
	
//    	while (true) {
//    		Thread.sleep(1000);
//    	}
	}
}
