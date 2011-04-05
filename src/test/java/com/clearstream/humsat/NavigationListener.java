package com.clearstream.humsat;

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.log4j.Logger;
import org.hbird.exchange.orbital.LocationContactEvent;
import org.hbird.exchange.orbital.OrbitalState;

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
public class NavigationListener {	
	
	protected static Logger logger = Logger.getLogger(NavigationListener.class);
	
	public void process(Exchange exchange) {
		Object pojo = exchange.getIn().getBody();
		if (pojo instanceof OrbitalState) {
			OrbitalState state = (OrbitalState) pojo;
			Date date = new Date(state.getTimestamp());
			logger.info("OrbitalState: " + state.getName() + " at " + date + ". Position={" + state.position.p1.getValue() + ", " + state.position.p2.getValue() + ", " + state.position.p3.getValue() + "}");
		}
		else if (pojo instanceof LocationContactEvent) {
			LocationContactEvent event = (LocationContactEvent) pojo;
			Date date = new Date(event.getTimestamp());
			logger.info("LocationContactEvent: " + event.getName() + " at " + date);
		}
		else {
			logger.error("Receiver object of type " + pojo.getClass());
		}
	}
}
