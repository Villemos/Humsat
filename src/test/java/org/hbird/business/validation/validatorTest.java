/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.validation;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hbird.exchange.type.Parameter;
import org.hbird.exchange.type.StateParameter;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Integration test for the validate component (upper/lower limit).
 * 
 * //FIXME It would be nice to use JUnit's parameterized testing feature. I
 * tried it, but it won't load the application context.
 */
public class validatorTest extends  TestCase {
	protected static boolean thisIsTheFirstRun = true;

	// uri = "mock:ResultsWarning"
	protected MockEndpoint resultsWarning = null;

	// uri = "mock:ResultsError"
	protected MockEndpoint resultsError = null;

	// uri = "mock:ResultsSwitch"
	protected MockEndpoint resultsSwitch = null;

	// uri = "mock:ResultsParameter"
	protected MockEndpoint resultsParameters = null;

	// uri = "activemq:topic:Parameters"
	protected ProducerTemplate producer = null;

	protected static CamelContext validatorContext = null;


	public void setUp() throws Exception {
		System.out.println("Setup start");
		// Check if the initialization has been run already. Between the
		// tests, the Camel context stays the same, so routes may not be added
		// during the initialization phase of further tests. Although, after 
		// e.g. 'testStateSwitch' which dirties the context, it has to be run
		// again.
		if (thisIsTheFirstRun) {
			System.out.println("first initialization");
			
			// Load contexts
			ApplicationContext temp;
			temp = new FileSystemXmlApplicationContext("file:src/main/resources/parameterValidator/validator.xml");
			
			validatorContext = (CamelContext) temp.getBean("validatorContext");
			validatorContext.start();
	
			// Add routes to access activemq topics via via mock endpoints.
			validatorContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:topic:ParametersWarning").to("mock:ResultsWarning");

					from("activemq:topic:ParametersError").to("mock:ResultsError");

					from("activemq:topic:ParametersSwitch").to("mock:ResultsSwitch");

					from("activemq:topic:Parameters").to("mock:ResultsParameter");
				}

			});
			
			thisIsTheFirstRun = false;
			
			System.out.println("first initialization end");

		}
		
		// Prepare producer
		producer = validatorContext.createProducerTemplate();


		// Prepare access to mock components
		resultsWarning = validatorContext.getEndpoint("mock:ResultsWarning", MockEndpoint.class);
		resultsError = validatorContext.getEndpoint("mock:ResultsError", MockEndpoint.class);
		resultsSwitch = validatorContext.getEndpoint("mock:ResultsSwitch", MockEndpoint.class);
		resultsParameters = validatorContext.getEndpoint("mock:ResultsParameter", MockEndpoint.class);

		// In case that there are still old parameters left in the
		// parameters
		// topic, wait until all have been routed to the 'results'
		// components, so that
		// they don't disturb the testing.
		int oldCount = -1;
		int newCount = 0;

		while (oldCount < newCount) {
			Thread.sleep(250);
			oldCount = newCount;
			newCount = resultsSwitch.getReceivedCounter()
					+ resultsWarning.getReceivedCounter()
					+ resultsError.getReceivedCounter()
					+ resultsParameters.getReceivedCounter();
		}

		// Reset Mock endpoints so that they don't contain any messages.
		resultsSwitch.reset();
		resultsWarning.reset();
		resultsError.reset();
		resultsParameters.reset();
		System.out.println("Setup end");
	}

	/*
	 * UpperLimit Test Tests temperature parameter with a value of '10'. Below
	 * all limits: no warning, no error.
	 */
	@Test
	public void testStateSwitch() throws Exception {
		// Send invalid parameter: 9 Volts is below Humsat's 10 Volts
		// warning-limit.
		Parameter invalidParameter = new Parameter("BATTERY_VOLTAGE",
				"This is an invalid battery voltage.",
				System.currentTimeMillis(), 9, "Volts");

		producer.sendBodyAndHeader("activemq:topic:Parameters", invalidParameter, "name", "BATTERY_VOLTAGE");

		waitForMessagesInMockEndpoints(0, 1, 1, 1);
		assertEquals("Warning-state of 9 Volt parameter is incorrect.",
				false,
				(boolean) resultsWarning.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());
		assertEquals("Error-state of 9 Volt parameter is incorrect.",
				true,
				(boolean) resultsError.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());

		// Set the new limit to 7 Volts.
		Parameter stateChangeParameter = new Parameter(
				"BATTERY_VOLTAGE_UPDATE",
				"This is a change of the battery voltage limit.",
				System.currentTimeMillis(), 7, "Volts");

		producer.sendBodyAndHeader("activemq:topic:Parameters", stateChangeParameter, "name", "BATTERY_VOLTAGE_UPDATE");

		waitForMessagesInMockEndpoints(1, 1, 1, 2);
		assertEquals("Counter of received switch-parameters is incorrect.", 1,
				resultsSwitch.getReceivedCounter());

		// Send valid parameter: 8 Volts is not below Humsat's new 7 Volts
		// warning-limit.
		Parameter validParameter = new Parameter("BATTERY_VOLTAGE",
				"This is an valid battery voltage.",
				System.currentTimeMillis(), 8, "Volts");

		producer.sendBodyAndHeader("activemq:topic:Parameters", validParameter, "name", "BATTERY_VOLTAGE");

		waitForMessagesInMockEndpoints(1, 2, 2, 3);

		assertEquals("Warning-state of 8 Volt parameter is incorrect.",
				true,
				(boolean) resultsWarning.getReceivedExchanges().get(1).getIn().getBody(StateParameter.class).getStateValue());
		assertEquals("Error-state of 8 Volt parameter is incorrect.",
				true,
				(boolean) resultsError.getReceivedExchanges().get(1).getIn().getBody(StateParameter.class).getStateValue());
		assertEquals("Counter of received 'parameters' is incorrect.",
				3,
				resultsParameters.getReceivedCounter());

		System.out.println("Limit-update test finished successfully.");
		
		// This test alters the current context, so it has to be stoppend and deleted. 
		validatorContext.stop();
		validatorContext = null;
		thisIsTheFirstRun = true;
	}

	/*
	 * UpperLimit Test Tests temperature parameter with a value of '10'. Below
	 * all limits: no warning, no error.
	 */
	@Test
	public void testValidCpuTemperatureParameter() throws Exception {
		int[] expectedMessages = { 0, 1, 1, 1 };
		boolean[] expectedStates = { true, true };
		String name = "CPU_TEMPERATURE";
		int value = 30;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("UpperLimit validation (no warning, no error) finished successfully.");
	}

	/*
	 * UpperLimit Test Tests temperature parameter with a value of '30'. Below
	 * the error limit: 1 warning, no error.
	 */
	@Test
	public void testWarningCpuTemperatureParameter() throws Exception {
		int[] expectedMessages = { 0, 1, 1, 1 }; // State, Warning, Error,
													// Parameter
		boolean[] expectedStates = { false, true }; // Warning, Error
		String name = "CPU_TEMPERATURE";
		int value = 50;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("UpperLimit validation (1 warning, no error) finished successfully.");
	}

	/*
	 * UpperLimit Test Tests temperature parameter with a value of '70'. Above
	 * all limits: 1 warning, 1 error.
	 */
	@Test
	public void testErrorCpuTemperatureParameter() throws Exception {
		int[] expectedMessages = { 0, 1, 1, 1 }; // State, Warning, Error,
													// Parameter
		boolean[] expectedStates = { false, false }; // Warning, Error
		String name = "CPU_TEMPERATURE";
		int value = 70;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("UpperLimit validation (1 warning, 1 error) finished successfully.");
	}

	/*
	 * LowerLimit Test Tests BATTERY_VOLTAGE parameter with a value of '12'.
	 * Above all limits: no warning, no error.
	 */
	@Test
	public void testValidBatteryVoltageParameter() throws Exception {
		int[] expectedMessages = { 0, 1, 1, 1 }; // State, Warning, Error,
													// Parameter
		boolean[] expectedStates = { true, true }; // Warning, Error
		String name = "BATTERY_VOLTAGE";
		int value = 12;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("LowerLimit validation (no warning, no error) finished successfully.");
	}

	/*
	 * LowerLimit Test Tests BATTERY_VOLTAGE parameter with a value of '8'.
	 * Below warning limit: 1 warning, no error.
	 */
	@Test
	public void testWarningBatteryVoltageParameter() throws Exception {
		int[] expectedMessages = { 0, 1, 1, 1 }; // State, Warning, Error,
													// Parameter
		boolean[] expectedStates = { false, true }; // Warning, Error
		String name = "BATTERY_VOLTAGE";
		int value = 8;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("LowerLimit validation (1 warning, no error) finished successfully.");
	}

	/*
	 * LowerLimit Test Tests BATTERY_VOLTAGE parameter with a value of '4'.
	 * Below all limits: 1 warning, 1 error.
	 */
	@Test
	public void testErrorBatteryVoltageParameter() throws Exception {
		int[] expectedMessages = { 0, 1, 1, 1 }; // State, Warning, Error,
													// Parameter
		boolean[] expectedStates = { false, false }; // Warning, Error
		String name = "BATTERY_VOLTAGE";
		int value = 4;

		runTest(expectedMessages, expectedStates, name, value);

		System.out.println("LowerLimit validation (1 warning, 1 error) finished successfully.");
	}

	/**
	 * Method to run the actual test, since the tests itself are all the same
	 * only with different parameters.
	 * 
	 * @param expectedMessages
	 *            4 integers for expected messages: state, warning, error,
	 *            parameter
	 * @param expectedStates
	 *            2 booleans for expected states: warning, error
	 * @param name
	 *            name of the parameter
	 * @param value
	 *            value of the parameter
	 * @throws Exception
	 */
	private void runTest(int[] expectedMessages, boolean[] expectedStates,
			String name, int value) {
		Parameter testParameter = new Parameter(name, "dummy description...", System.currentTimeMillis(), value, "dummy unit...");
		
		if(validatorContext == null) System.out.println("PRODUCER IS NULL");
		producer.sendBodyAndHeader("activemq:topic:Parameters", testParameter, "name", name);

		waitForMessagesInMockEndpoints(expectedMessages[0],	expectedMessages[1], expectedMessages[2], expectedMessages[3]);

		assertEquals("Wrong number of 'switch' state-parameters received.",
				expectedMessages[0], 
				resultsSwitch.getReceivedCounter());
		assertEquals("Wrong number of 'warning' state-parameters received.",
				expectedMessages[1], 
				resultsWarning.getReceivedCounter());
		assertEquals("Wrong number of 'error' state-parameters received.",
				expectedMessages[2], 
				resultsError.getReceivedCounter());
		assertEquals("Wrong number of 'parameter' received.",
				expectedMessages[3], 
				resultsParameters.getReceivedCounter());

		assertEquals("'warning' state-parameter for " + name + " with value of '" + value + "' has a false state.",
				expectedStates[0], 
				(boolean) resultsWarning.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());

		assertEquals("'error' state-parameter for " + name + " with value of '"	+ value + "' has a false state.",
				expectedStates[1],
				(boolean) resultsError.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());
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
			int resultsWarningCount, int resultErrorCount,
			int resultsParameterCount) {
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
}
