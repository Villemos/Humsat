/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.validation;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hbird.exchange.type.Parameter;
import org.hbird.exchange.type.StateParameter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * Integration test for the validate component (upper/lower limit).
 * 
 */
/**
 * @author tobias
 *
 */
@RunWith(Parameterized.class)
public class ValidatorTest extends  TestCase {
	protected static boolean thisIsTheFirstRun = true;
	
	protected static CamelContext validatorContext = null;

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
	
	int expectedSwitch;
	int expectedWarning;
	int expectedError;
	int expectedParameter;
	boolean warningState;
	boolean errorState;
	String name;
	
	int value;
	String nameOfTest;
	
	/**
	 * Provider of parameters for parameterized test.
	 * 
	 * @return
	 */
	@Parameters
	public static Collection<Object[]> input() {

		// The array used for testing represents the parameters in the ValidatorTest constructor 
		ArrayList<Object[]> parameters = new ArrayList<Object[]>();
		parameters.add(new Object[] { 0, 1, 1, 1, true, true, "CPU_TEMPERATURE", 30});
		parameters.add(new Object[] { 0, 1, 1, 1, false, true, "CPU_TEMPERATURE", 50});
		parameters.add(new Object[] { 0, 1, 1, 1, false, false, "CPU_TEMPERATURE", 70});
		parameters.add(new Object[] { 0, 1, 1, 1, true, true, "BATTERY_VOLTAGE", 12});
		parameters.add(new Object[] { 0, 1, 1, 1, false, true, "BATTERY_VOLTAGE", 8});
		parameters.add(new Object[] { 0, 1, 1, 1, false, false, "BATTERY_VOLTAGE", 4});
		
		return parameters;
	}
	
	/**
	 * Constructor which is used during the parameterized test.
	 * 
	 * @param expectedSwitch
	 * @param expectedWarning
	 * @param expectedError
	 * @param expectedParameter
	 * @param warningState
	 * @param errorState
	 * @param name
	 * @param value
	 * @throws Exception
	 */
	public ValidatorTest(int expectedSwitch, int expectedWarning, int expectedError, int expectedParameter, boolean warningState, boolean errorState, String name, int value) throws Exception {
		// Between tests the Camel context stays the same, so routes may not 
		// be added during the initialization phase of further tests.
		if (thisIsTheFirstRun) {
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
		}
		
		// Prepare producer
		producer = validatorContext.createProducerTemplate();

		// Prepare access to mock components
		resultsWarning = validatorContext.getEndpoint("mock:ResultsWarning", MockEndpoint.class);
		resultsError = validatorContext.getEndpoint("mock:ResultsError", MockEndpoint.class);
		resultsSwitch = validatorContext.getEndpoint("mock:ResultsSwitch", MockEndpoint.class);
		resultsParameters = validatorContext.getEndpoint("mock:ResultsParameter", MockEndpoint.class);

		// In case that there are still old parameters left in the parameters topic, wait until
		// all have been routed to the 'results' components, so that they don't disturb the testing.
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
		
		// Set values for parameterized test
		this.expectedSwitch = expectedSwitch;
		this.expectedWarning = expectedWarning;
		this.expectedError = expectedError;
		this.expectedParameter = expectedParameter;		
		this.warningState = warningState;
		this.errorState = errorState;
		this.name = name;
		this.value = value;
		
		nameOfTest = "\nTest of '" + name + "' with value  '" + value + "':\n";
	}
	
	
	/**
	 * Method to run the actual test. Will use the different parameters
	 * provided by the input() method.
	 */
	@Test
	public void validatorTest() throws Exception {
		// Prepare and send parameter
		Parameter testParameter = new Parameter(name, "dummy description...", System.currentTimeMillis(), value, "dummy unit...");
		
		producer.sendBodyAndHeader("activemq:topic:Parameters", testParameter, "name", name);

		waitForMessagesInMockEndpoints(expectedSwitch, expectedWarning, expectedError, expectedParameter);
        
		//Validate conditions: correct number of received messages
		assertEquals(nameOfTest + "Wrong number of 'switch' state-parameters received.",
				expectedSwitch, 
				resultsSwitch.getReceivedCounter());
		assertEquals(nameOfTest + "Wrong number of 'warning' state-parameters received.",
				expectedWarning, 
				resultsWarning.getReceivedCounter());
		assertEquals(nameOfTest +"Wrong number of 'error' state-parameters received.",
				expectedError, 
				resultsError.getReceivedCounter());
		assertEquals(nameOfTest + "Wrong number of 'parameter' received.",
				expectedParameter, 
				resultsParameters.getReceivedCounter());

		//Validate conditions: correct content of received messages
		assertEquals(nameOfTest + "'warning' state-parameter has a false state.",
				warningState, 
				(boolean) resultsWarning.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());
		assertEquals(nameOfTest + "'error' state-parameter has a false state.",
				errorState,
				(boolean) resultsError.getReceivedExchanges().get(0).getIn().getBody(StateParameter.class).getStateValue());
	}
	
	
	/**
	 * Method which will wait until the specified number of message has been received in
	 * all 4 Mock-endpoints or until ~4 seconds have passed.
	 * 
	 * @param resultSwitchCount
	 * @param resultErrorCount
	 * @param resultsWarningCount
	 * @param resultsParameterCount
	 * @throws InterruptedException
	 */
	private void waitForMessagesInMockEndpoints(int resultSwitchCount, int resultsWarningCount, int resultErrorCount, int resultsParameterCount) throws InterruptedException {
		for (int i = 2; !(resultsSwitch.getReceivedCounter() >= resultSwitchCount
				&& resultsWarning.getReceivedCounter() >= resultsWarningCount
				&& resultsError.getReceivedCounter() >= resultErrorCount && resultsParameters
				.getReceivedCounter() >= resultsParameterCount) && i < 4096; i *= 2) {

			Thread.sleep(i);
		}
	}
}
