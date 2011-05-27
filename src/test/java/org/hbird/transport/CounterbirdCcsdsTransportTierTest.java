/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.transport;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.log4j.Logger;
import org.hbird.business.simulator.SimulatorSSM;
import org.hbird.business.simulator.waveforms.FlatWaveform;
import org.hbird.business.simulator.waveforms.Waveform;
import org.hbird.exchange.type.Parameter;
import org.hbird.transport.protocols.ccsds.transferframe.encoder.CcsdsFrameEncoder;
import org.hbird.transport.xtce.XtceModelFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * The integration test will use the SimulatorSSM to create frames that can be processed
 * by the counterbird-ccsds-transport-tier. 
 * The Simulator itself creates a message containing a Byte array with the generated value.
 * This value is being encoded by the CcsdsFrameEncoder and then send to the activemq 
 * queue 'frames'.
 * From there on, the counterbird-ccsds-transport-tier takes over. 
 * 
 * @author turbat
 *
 */
@ContextConfiguration(locations = { "file:src/main/resources/humsat-counterbird-ccsds-transport-tier.xml" })
public class CounterbirdCcsdsTransportTierTest extends AbstractJUnit4SpringContextTests {
	protected static Logger logger = Logger.getLogger(CounterbirdCcsdsTransportTierTest.class);
	
	protected SimulatorSSM simulator = null;

	@Autowired
	protected CamelContext counterbirdTransportTierContext = null;
	
	@Produce(uri = "seda:simMessages")
	protected ProducerTemplate producer = null;
	
	@EndpointInject(uri = "mock:Results")
	protected MockEndpoint results = null;

	@Before
	public void initialize() throws Exception {
		//Create a new simulator. It will not be used to for proper simulation, 
		//but to generate the values needed for the integration test. 
		XtceModelFactory factory = new XtceModelFactory("src/data/cubesat.xml");
		simulator = new SimulatorSSM(factory, "TMPacket");

		//The producer set in the simulator does not work, because
		//it was not situated (?) in a camel context during instantiation. 
		simulator.setTemplate(producer);

		//Add the route that will process the values generated by the simulator.
		counterbirdTransportTierContext.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				from("seda:simMessages")
				.split().method(new CcsdsFrameEncoder(256))
				.to("activemq:frames");
			}
		});
		

		
		
		//Add a route to access the parameters given out by the transport tier. 
		counterbirdTransportTierContext.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				from("activemq:topic:Parameters").to("mock:Results");
			}
		});
	}
	
	@Test
	public void testTransportTier() throws InterruptedException {
		generateCubesatPositionTelemetryPacket(20);
		
		for (int i = 4; results.getReceivedCounter() < 3  && i < 8192; i *= 2) {
			Thread.sleep(i);
		}
		
		System.out.println("\n\n--------------------------------------------------------------");
		for(Exchange e : results.getReceivedExchanges()) {
			System.out.println(e.getIn().getBody(Parameter.class).getName()
					+ "\t " 
					+ e.getIn().getBody(Parameter.class).getValue());
		}
		System.out.println("\n\n--------------------------------------------------------------");
			
		
	}

	@After
	public void tearDown() {
	}
	
	/**
	 * Method to generate a value using the SimulatorSSM. The values
	 * will be send to 'seda:simMessages' (specified in the SimulatorSSM)
	 * and then processed by the route defined in the initialize().
	 * 
	 * @param value		Value to be generated.
	 */
	private void generateCubesatPositionTelemetryPacket(double d) {
		Map<String,Waveform> test = new HashMap<String,Waveform>();
		test.put("CUBESAT_APID", new FlatWaveform(111));
		test.put("PACKET_LENGTH", new FlatWaveform(96));
		test.put("ELEVATION", new FlatWaveform(d));
		test.put("LONGITUDE", new FlatWaveform(0));
		test.put("LATITUDE", new FlatWaveform(0));
		
 		simulator.setWaveformMap(test);
 		simulator.generateMessage();
	}
}
