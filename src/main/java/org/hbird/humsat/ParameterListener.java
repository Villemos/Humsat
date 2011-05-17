package org.hbird.humsat;

import org.apache.camel.Exchange;
import org.apache.log4j.Logger;
import org.hbird.exchange.type.Parameter;

public class ParameterListener {

	protected static Logger logger = Logger.getLogger(ParameterListener.class);
	
	public void process(Exchange exchange) {
		Object pojo = exchange.getIn().getBody();
		if (pojo instanceof Parameter) {
			Parameter parameter = (Parameter) pojo;
			logger.info("Parameter: " + parameter.getName() + " == " + parameter.getValue().toString());
		}
		else {
			logger.error("Pojo of type '" + pojo.getClass() + "' not a Parameter.");
		}		
	}
}
