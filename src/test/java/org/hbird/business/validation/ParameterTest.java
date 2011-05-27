/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.validation;

import static org.junit.Assert.assertEquals;

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
 * //FIXME Get test to use JUnit's parameterized feature. Tried it, but it doesn't seem to load the application context. 
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
			Thread.sleep(250);
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
		int[] expectedMessages = {0, 1 , 1, 1}; 
		boolean[] expectedStates = {true, true};
		String name = "CPU_TEMPERATURE";
		int value = 30;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("UpperLimit Validation (no warning, no error) finished successfully.");
	}

	/*
	 * UpperLimit Test
	 * Tests temperature parameter with a value of '30'. 
	 * Below the error limit: 1 warning, no error.
	 */
	@Test
	public void testWarningCpuTemperatureParameter() throws Exception {
		int[] expectedMessages = {0, 1 , 1, 1};  //State, Warning, Error, Parameter
		boolean[] expectedStates = {false, true}; //Warning, Error
		String name = "CPU_TEMPERATURE";
		int value = 50;

		runTest(expectedMessages, expectedStates, name, value);
		
		System.out.println("UpperLimit Validation (1 warning, no error) finished successfully.");
	}

	/*
	 * UpperLimit Test
	 * Tests temperature parameter with a value of '70'. 
	 * Above all limits: 1 warning, 1 error.
	 */
	@Test
	public void testErrorCpuTemperatureParameter() throws Exception {
		int[] expectedMessages = {0, 1 , 1, 1};  //State, Warning, Error, Parameter
		boolean[] expectedStates = {false, false}; //Warning, Error
		String name = "CPU_TEMPERATURE";
		int value = 70;

		runTest(expectedMessages, expectedStates, name, value);
		
		System.out.println("UpperLimit Validation (1 warning, 1 error) finished successfully.");
	}
	
	
	/*
	 * LowerLimit Test
	 * Tests BATTERY_VOLTAGE parameter with a value of '12'.
	 * Above all limits: no warning, no error.
	 */
	@Test
	public void testValidBatteryVoltageParameter() throws Exception {
		int[] expectedMessages = {0, 1 , 1, 1};  //State, Warning, Error, Parameter
		boolean[] expectedStates = {true, true}; //Warning, Error
		String name = "BATTERY_VOLTAGE";
		int value = 12;

		runTest(expectedMessages, expectedStates, name, value);
		
		System.out.println("LowerLimit Validation (no warning, no error) finished successfully.");
	}

	/*
	 * LowerLimit Test
	 * Tests BATTERY_VOLTAGE parameter with a value of '8'.
	 * Below warning limit: 1 warning, no error.
	 */
	@Test
	public void testWarningBatteryVoltageParameter() throws Exception {
		int[] expectedMessages = {0, 1 , 1, 1};  //State, Warning, Error, Parameter
		boolean[] expectedStates = {false, true}; //Warning, Error
		String name = "BATTERY_VOLTAGE";
		int value = 8;

		runTest(expectedMessages, expectedStates, name, value);
		
		System.out.println("LowerLimit Validation (1 warning, no error) finished successfully.");
	}

	/*
	 * LowerLimit Test
	 * Tests BATTERY_VOLTAGE parameter with a value of '4'.
	 * Below all limits: 1 warning, 1 error.
	 */
	@Test
	public void testErrorBatteryVoltageParameter() throws Exception {
		int[] expectedMessages = {0, 1 , 1, 1};  //State, Warning, Error, Parameter
		boolean[] expectedStates = {false, false}; //Warning, Error
		String name = "BATTERY_VOLTAGE";
		int value = 4;

		runTest(expectedMessages, expectedStates, name, value);
		
		System.out.println("LowerLimit Validation (1 warning, 1 error) finished successfully.");
	}
	
	/**
	 * Method to run the actual test, since the tests itself are all the same only with different parameters. 
	 * 
	 * @param expectedMessages 4 integers for expected messages: state, warning, error, parameter
	 * @param expectedStates   2 booleans for expected states: warning, error
	 * @param name			   name of the parameter 
	 * @param value            value of the parameter
	 * @throws Exception
	 */
	private void runTest(int[] expectedMessages, boolean[] expectedStates, String name, int value) {
		Parameter testParameter = new Parameter(name, "dummy description...",
				System.currentTimeMillis(), value, "dummy unit...");
		
		producer.sendBodyAndHeader(testParameter, "name", name);

		waitForMessagesInMockEndpoints(expectedMessages[0], expectedMessages[1], expectedMessages[2], expectedMessages[3]);

		assertEquals("Wrong number of 'switch' state-parameters received.", expectedMessages[0], resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.", expectedMessages[1], resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'error' state-parameters received.", expectedMessages[2], resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.", expectedMessages[3], resultsParameters.getReceivedCounter());
		
		assertEquals("'warning' state-parameter for "+ name + " with value of '" + value + "' has a false state.", 
				expectedStates[0], 
				resultsWarning.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());

		assertEquals("'error' state-parameter for "+ name + " with value of '" + value + "' has a false state.", 
				expectedStates[1], 
				resultsError.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());
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
	private void waitForMessagesInMockEndpoints(int resultSwitchCount, 
			int resultsWarningCount,int resultErrorCount, int resultsParameterCount)  {
		for (int i = 2; !(resultsSwitch.getReceivedCounter() >= resultSwitchCount
				&& resultsWarning.getReceivedCounter() >= resultsWarningCount
				&& resultsError.getReceivedCounter() >= resultErrorCount && resultsParameters
				.getReceivedCounter() >= resultsParameterCount) && i < 4096; i *= 2) {

			try {
				Thread.sleep(i);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

	@After
	public void tearDown() {
	}
}
