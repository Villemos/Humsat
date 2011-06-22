/**
 * Licensed to the Hummingbird Foundation (HF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The HF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hbird.business.navigation;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.hbird.exchange.navigation.LocationContactEvent;
import org.hbird.exchange.navigation.OrbitalState;
import org.hbird.exchange.type.D3Vector;
import org.hbird.exchange.type.Location;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Tests the configuration of the orbit prediction scheduler and whether the prediction works.
 *
 */
@ContextConfiguration(locations = { "file:src/main/resources/navigation/orbit-predictor-orekit.xml" })
public class OrbitPredictionTest extends AbstractJUnit4SpringContextTests {

	@Produce(uri = "direct:Start")
	protected ProducerTemplate template;

	@Produce(uri = "activemq:topic:NavigationComponentConfiguration")
	protected ProducerTemplate updateConfiguration;
		
	@EndpointInject(uri = "mock:OrbitPredictions")
	protected MockEndpoint orbitPredictions;

	@Autowired
	protected CamelContext orbitPredictorContext;


	@Before
	public void initialize() throws Exception {
		orbitPredictorContext.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				from("activemq:topic:OrbitPredictions").to("mock:OrbitPredictions");
			}
		});
	}
	
	@Test
	public void testOrbitPrediction() throws InterruptedException {
		Exchange exchange;
		Location groundStation = new Location("Donzdorf", "Another ground station in Germany", 
				new D3Vector("Position", "The position", 48.69096, 9.84375, 0.));
		D3Vector position = new D3Vector("", "", -6142438.668, 3492467.560, -25767.25680);
		D3Vector velocity = new D3Vector("", "", 505.8479685, 942.7809215, 7435.922231);

		
		// Configure OrbitPredictionScheduler.
		exchange = new DefaultExchange(orbitPredictorContext);
		exchange.getIn().setBody(60);
		exchange.getIn().setBody(50);
		exchange.getIn().setHeader("name", "STEP_SIZE");
		updateConfiguration.send(exchange);

		exchange = new DefaultExchange(orbitPredictorContext);
		exchange.getIn().setBody(3600);
		exchange.getIn().setBody(7200);
		exchange.getIn().setHeader("name", "DELTA_PROPAGATION");
		updateConfiguration.send(exchange);

		exchange = new DefaultExchange(orbitPredictorContext);
		exchange.getIn().setBody(86400000);
		exchange.getIn().setBody(40000);
		exchange.getIn().setHeader("name", "PREDICTION_INTERVAL");
		updateConfiguration.send(exchange);

		// Add ground stations to orbit prediction scheduler
		exchange = new DefaultExchange(orbitPredictorContext);
		exchange.getIn().setBody(groundStation);
		exchange.getIn().setHeader("name", "LOCATION");
		updateConfiguration.send(exchange);

		// set orbit predictor context (this will start the prediction)
		exchange = new DefaultExchange(orbitPredictorContext);
		long now = System.currentTimeMillis();
		exchange.getIn().setBody(new OrbitalState("Measured", "...", now, now, position, velocity));
		exchange.getIn().setHeader("name", "ORBITAL_STATE");
		updateConfiguration.send(exchange);

		assertEquals("Number of messages in orbit predictions topic (before the actual prediction) is wrong. ", 
				0, orbitPredictions.getReceivedCounter());

		// wait...
		waitUntilPredictionIsDone(0);

		// Split received messages into OrbitalStates and LocationContactEvents
		List<OrbitalState> orbitalStates = new ArrayList<OrbitalState>();
		List<LocationContactEvent> locationContactEvents = new ArrayList<LocationContactEvent>();

		for (Exchange out : orbitPredictions.getExchanges()) {
			if (out.getIn().getBody() instanceof OrbitalState) {
				orbitalStates.add((OrbitalState) out.getIn().getBody());
			} else if (out.getIn().getBody() instanceof LocationContactEvent) {
				locationContactEvents.add((LocationContactEvent) out.getIn().getBody());
			} else {
				fail("Message in the OrbitPredictions topic is of type " + out.getIn().getBody().getClass().getName()
						+ ". Only OrbitalState and LocationContactEvent is allowed.");
			}
		}
		
//		System.out.println("\nop: " + orbitalStates.size() + " \t " + "lce: " + locationContactEvents.size());
		
//		for(LocationContactEvent l : locationContactEvents) {
//			System.out.println(l.location.getPosition().p1.getValue() + " \t " + l.location.getPosition().p2.getValue() + " \t " + l.location.getPosition().p3.getValue());
//		}

//		System.out.println("\n\n");
//		assertEquals("Number of predicted LocationContactEvents is incorrect.", 6, locationContactEvents.size());
		assertEquals("Number of predicted OrbitalStates is incorrect.", 145, orbitalStates.size());
		
//		for(OrbitalState o : orbitalStates) {
//			System.out.println(o.position.p1.getValue() + " \t " 
//					+ o.position.p2.getValue() + " \t " 
//					+ o.position.p3.getValue() + " \t " 
//					+ o.getTimestamp());
//		}
	}
	
	
	/**
	 * Wait until prediction is done and no more messages are received
	 * @throws InterruptedException
	 */
	public void waitUntilPredictionIsDone(int startMessageCounter) throws InterruptedException {
		// Wait until receiving of messages starts
		while (orbitPredictions.getReceivedCounter() == startMessageCounter) {
			Thread.sleep(250);
		}

		// Wait max ~16sec until no more messages are received.
		int oldCount = -1;
		int newCount = 0;		

		for (int i = 4; oldCount < newCount && i < 32768; i *= 2) {
			Thread.sleep(250);
			oldCount = newCount;
			newCount = orbitPredictions.getReceivedCounter();

			Thread.sleep(i);
		}
		
		Thread.sleep(500);
	}
}
