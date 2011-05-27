/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.validation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hbird.exchange.type.Parameter;
import org.hbird.exchange.type.StateParameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Integration test for 'Validator' Component.
 *
 */
@ContextConfiguration(locations = { "file:src/main/resources/humsat-validator.xml" })
public class ParameterTest extends AbstractJUnit4SpringContextTests {
	@EndpointInject(uri = "mock:ResultsWarning")
	protected MockEndpoint resultsWarning = null;

	@EndpointInject(uri = "mock:ResultsError")
	protected MockEndpoint resultsError = null;

	@EndpointInject(uri = "mock:ResultsSwitch")
	protected MockEndpoint resultsSwitch = null;

	@EndpointInject(uri = "mock:ResultsParameter")
	protected MockEndpoint resultsParameters = null;

	@Produce(uri = "activemq:topic:Parameters")
	protected ProducerTemplate producer = null;

	@Autowired
	protected CamelContext validatorContext = null;

	@Before
	public void initialize() throws Exception {
		// The 'humsat-validator' context consists of 12 routes 
		// only. If there are more routes, the initialization 
		// method has been run already. The additional routes needed 
		// for this integration test may not be added again. 
		List<Route> routes = validatorContext.getRoutes();

		if (routes.size() == 12) {
			// Add a route to access activemq:topic:ParametersWarning via a mock endpoint.
			validatorContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:topic:ParametersWarning").to("mock:ResultsWarning");
				}
			});

			// Add a route to access activemq:topic:ParametersError via a mock endpoint.
			validatorContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:topic:ParametersError").to("mock:ResultsError");
				}
			});

			// Add a route to access activemq:topic:ParametersSwitch via a mock endpoint.
			validatorContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:topic:ParametersSwitch").to("mock:ResultsSwitch");
				}
			});

			// Add a route to access activemq:topic:Parameters tier via a mock endpoint.
			validatorContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:topic:Parameters").to("mock:ResultsParameter");
				}
			});
		}

		// In case that there are still old parameters left in the parameters topic,
		// wait until all have been routed to the 'results' components, so that they
		// don't disturb the testing.
		int oldCount = -1;
		int newCount = 0;
		while (oldCount < newCount) {
			Thread.sleep(1200);
			oldCount = newCount;
			newCount = resultsSwitch.getReceivedCounter() + resultsWarning.getReceivedCounter()
					+ resultsError.getReceivedCounter()	+ resultsParameters.getReceivedCounter();
		}

		// Reset Mock endpoints so that they don't contain any messages.
		resultsSwitch.reset();
		resultsWarning.reset();
		resultsError.reset();
		resultsParameters.reset();

	}

	/*
	 * UpperLimit Test
	 * Tests temperature parameter with a value of '10'.
	 * Below all limits: no warning, no error.
	 */
	@Test
	public void testValidCpuTemperatureParameter() throws Exception {
		Parameter testParameter = new Parameter("CPU_TEMPERATURE", "Temperature of CPU board...",
				System.currentTimeMillis(), 10, "Degree celsius");
		
		producer.sendBodyAndHeader(testParameter, "name", "CPU_TEMPERATURE");

		awaitMessagesInResultsSwitchWarningErrorParameter(0, 1, 1, 1);

		assertEquals("Wrong number of 'switch' state-parameters received.", 0, resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", 1, resultsParameters.getReceivedCounter());
		
		assertTrue("CPU_Temperature of '10' should result in NO 'warning'.", resultsWarning.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());

		assertTrue("CPU_Temperature of '10' should result in NO 'error'.", resultsError.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());

		System.out.println("UpperLimit Validation (no warning, no error) finished successfully.");
	}

	/*
	 * UpperLimit Test
	 * Tests temperature parameter with a value of '30'. 
	 * Below the error limit: 1 warning, no error.
	 */
	@Test
	public void testWarningCpuTemperatureParameter() throws Exception {
		Parameter testParameter = new Parameter("CPU_TEMPERATURE", "Temperature of CPU board...",
				System.currentTimeMillis(), 30, "Degree celsius");
		
		producer.sendBodyAndHeader(testParameter, "name", "CPU_TEMPERATURE");

		awaitMessagesInResultsSwitchWarningErrorParameter(0, 1, 1, 1);

		assertEquals("Wrong number of 'switch' state-parameters received.", 0, resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", 1, resultsParameters.getReceivedCounter());
		
		assertFalse("CPU_Temperature of '30' should result in 1 'warning'.", resultsWarning.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());
		assertTrue("CPU_Temperature of '30' should result in NO 'error'.", resultsError.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());
		
		System.out.println("UpperLimit Validation (1 warning, no error) finished successfully.");
	}

	/*
	 * UpperLimit Test
	 * Tests temperature parameter with a value of '70'. 
	 * Below all limits: 1 warning, 1 error.
	 */
	@Test
	public void testErrorCpuTemperatureParameter() throws Exception {
		Parameter testParameter = new Parameter("CPU_TEMPERATURE", "Temperature of CPU board...",
				System.currentTimeMillis(), 70, "Degree celsius");
		
		producer.sendBodyAndHeader(testParameter, "name", "CPU_TEMPERATURE");

		awaitMessagesInResultsSwitchWarningErrorParameter(0, 1, 1, 1);

		assertEquals("Wrong number of 'switch' state-parameters received.", 0, resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", 1, resultsParameters.getReceivedCounter());

		assertFalse("CPU_Temperature of '70' should result in 1 'warning'.", resultsWarning.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());
		assertFalse("CPU_Temperature of '70' should result in 1 'error'.", resultsError.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());

		System.out.println("UpperLimit Validation (1 warning, 1 error) finished successfully.");
	}
	
	
	/*
	 * LowerLimit Test
	 * Tests BATTERY_VOLTAGE parameter with a value of '12'.
	 * Above all limits: no warning, no error.
	 */
	@Test
	public void testValidBatteryVoltageParameter() throws Exception {
		Parameter testParameter = new Parameter("BATTERY_VOLTAGE", "Voltage of battery...",
				System.currentTimeMillis(), 12, "Volts");
		
		producer.sendBodyAndHeader(testParameter, "name", "BATTERY_VOLTAGE");

		awaitMessagesInResultsSwitchWarningErrorParameter(0, 1, 1, 1);

		assertEquals("Wrong number of 'switch' state-parameters received.", 0, resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", 1, resultsParameters.getReceivedCounter());
		
		assertTrue("BATTERY_VOLTAGE of '12' should result in NO 'warning'.", resultsWarning.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());

		assertTrue("BATTERY_VOLTAGE of '12' should result in NO 'error'.", resultsError.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());

		System.out.println("LowerLimit Validation (no warning, no error) finished successfully.");
	}

	/*
	 * LowerLimit Test
	 * Tests BATTERY_VOLTAGE parameter with a value of '8'.
	 * Above all limits: 1 warning, no error.
	 */
	@Test
	public void testWarningBatteryVoltageParameter() throws Exception {
		Parameter testParameter = new Parameter("BATTERY_VOLTAGE", "Voltage of battery...",
				System.currentTimeMillis(), 8, "Volts");
		
		producer.sendBodyAndHeader(testParameter, "name", "BATTERY_VOLTAGE");

		awaitMessagesInResultsSwitchWarningErrorParameter(0, 1, 1, 1);

		assertEquals("Wrong number of 'switch' state-parameters received.", 0, resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", 1, resultsParameters.getReceivedCounter());
		
		assertFalse("BATTERY_VOLTAGE of '8' should result in 1 'warning'.", resultsWarning.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());
		assertTrue("BATTERY_VOLTAGE of '8' should result in NO 'error'.", resultsError.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());
		
		System.out.println("LowerLimit Validation (1 warning, no error) finished successfully.");
	}

	/*
	 * LowerLimit Test
	 * Tests BATTERY_VOLTAGE parameter with a value of '4'.
	 * Above all limits: 1 warning, 1 error.
	 */
	@Test
	public void testErrorBatteryVoltageParameter() throws Exception {
		Parameter testParameter = new Parameter("BATTERY_VOLTAGE", "Voltage of battery...",
				System.currentTimeMillis(), 4, "Volts"); 
			
		producer.sendBodyAndHeader(testParameter, "name", "BATTERY_VOLTAGE");

		awaitMessagesInResultsSwitchWarningErrorParameter(0, 1, 1, 1);

		assertEquals("Wrong number of 'switch' state-parameters received.", 0, resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", 1, resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", 1, resultsParameters.getReceivedCounter());

		assertFalse("BATTERY_VOLTAGE of '4' should result in 1 'warning'.", resultsWarning.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());
		assertFalse("BATTERY_VOLTAGE of '4' should result in 1 'error'.", resultsError.getReceivedExchanges().get(0).getIn()
				.getBody(StateParameter.class).getStateValue());

		System.out.println("LowerLimit Validation (1 warning, 1 error) finished successfully.");
	}
	
	/**
	 * Method to wait until the specified number of message has been received in
	 * all 4 Mock-endpoints or until ~4 seconds have passed.
	 * 
	 * @param resultSwitchCount
	 * @param resultErrorCount
	 * @param resultsWarningCount
	 * @param resultsParameterCount
	 * @throws InterruptedException
	 */
	private void awaitMessagesInResultsSwitchWarningErrorParameter(int resultSwitchCount, 
			int resultsWarningCount,int resultErrorCount, int resultsParameterCount) throws Exception {
		for (int i = 2; !(resultsSwitch.getReceivedCounter() >= resultSwitchCount
				&& resultsWarning.getReceivedCounter() >= resultsWarningCount
				&& resultsError.getReceivedCounter() >= resultErrorCount && resultsParameters
				.getReceivedCounter() >= resultsParameterCount) && i < 4096; i *= 2) {

			Thread.sleep(i);

		}
	}

	@After
	public void tearDown() {
	}
}
