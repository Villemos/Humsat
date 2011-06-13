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

/**
 * Integration test for the validate component (upper/lower limit).
 * 
 */
public class ValidatorStateSwitchTest extends TestCase {
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

	protected CamelContext validatorContext = null;

	public ValidatorStateSwitchTest() throws Exception {
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

		// Prepare producer
		producer = validatorContext.createProducerTemplate();

		// Prepare access to mock components
		resultsWarning = validatorContext.getEndpoint("mock:ResultsWarning", MockEndpoint.class);
		resultsError = validatorContext.getEndpoint("mock:ResultsError", MockEndpoint.class);
		resultsSwitch = validatorContext.getEndpoint("mock:ResultsSwitch", MockEndpoint.class);
		resultsParameters = validatorContext.getEndpoint("mock:ResultsParameter", MockEndpoint.class);

		// In case that there are still old parameters left in the parameters
		// topic, wait
		// until all have been routed to the 'results' components, so that they
		// don't disturb the testing.
		int oldCount = -1;
		int newCount = 0;

		while (oldCount < newCount) {
			Thread.sleep(250);
			oldCount = newCount;
			newCount = resultsSwitch.getReceivedCounter() + resultsWarning.getReceivedCounter()
					   + resultsError.getReceivedCounter() + resultsParameters.getReceivedCounter();
		}

		// Reset Mock endpoints so that they don't contain any messages.
		resultsSwitch.reset();
		resultsWarning.reset();
		resultsError.reset();
		resultsParameters.reset();
	}

	/**
	 * UpperLimit Test Tests temperature parameter with a value of '10'. Below
	 * all limits: no warning, no error.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testStateSwitch() throws Exception {
		// Send invalid parameter: 9 Volts is below Humsat's 10 Volts
		// warning-limit.
		Parameter invalidParameter = new Parameter("BATTERY_VOLTAGE", "This is an invalid battery voltage.",
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
		Parameter stateChangeParameter = new Parameter("BATTERY_VOLTAGE_UPDATE",
			"This is a change of the battery voltage limit.", System.currentTimeMillis(), 7, "Volts");

		producer.sendBodyAndHeader("activemq:topic:Parameters", stateChangeParameter, "name", "BATTERY_VOLTAGE_UPDATE");

		waitForMessagesInMockEndpoints(1, 1, 1, 2);
		assertEquals("Counter of received switch-parameters is incorrect.", 1, resultsSwitch.getReceivedCounter());

		// Send valid parameter: 8 Volts is not below Humsat's new 7 Volts
		// warning-limit.
		Parameter validParameter = new Parameter("BATTERY_VOLTAGE", "This is an valid battery voltage.",
			System.currentTimeMillis(), 8, "Volts");

		producer.sendBodyAndHeader("activemq:topic:Parameters", validParameter, "name", "BATTERY_VOLTAGE");

		waitForMessagesInMockEndpoints(1, 2, 2, 3);

		assertEquals("Warning-state of 8 Volt parameter is incorrect.", 
			true, 
			(boolean) resultsWarning.getReceivedExchanges().get(1).getIn().getBody(StateParameter.class).getStateValue());
		assertEquals("Error-state of 8 Volt parameter is incorrect.", 
			true, 
			(boolean) resultsError.getReceivedExchanges().get(1).getIn().getBody(StateParameter.class).getStateValue());
		assertEquals("Counter of received 'parameters' is incorrect.", 3, resultsParameters.getReceivedCounter());
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
	private void waitForMessagesInMockEndpoints(int resultSwitchCount, int resultsWarningCount, int resultErrorCount,
			int resultsParameterCount) throws InterruptedException {
		for (int i = 2; !(
			resultsSwitch.getReceivedCounter() >= resultSwitchCount && resultsWarning.getReceivedCounter() >= resultsWarningCount
			&& resultsError.getReceivedCounter() >= resultErrorCount && resultsParameters.getReceivedCounter() >= resultsParameterCount)
			&& i < 4096; i *= 2) {

			Thread.sleep(i);
		}
	}
}
