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
import org.junit.Ignore;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * Upper class for the validator integration test. Both tests (LimitUpdate and UpperLowerLimit) use
 * the same context. 
 * They can not be put into the same test-class because the UpperLowerLimit is a parameterized test.
 * 
 * This class sets up the Camel context with all additional routes used for testing and provides access
 * to the mock endpoints.
 * It also contains a method to wait until a certain number of messages has been received in all
 * mock endpoints.
 * 
 */
@Ignore
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

	public ValidatorTest() throws Exception {
		// Between tests the Camel context stays the same, so routes may not 
		// be added during the initialization phase of further tests.
		if (thisIsTheFirstRun) {
			// Load contexts
			ApplicationContext temp;
			temp = new FileSystemXmlApplicationContext("file:src/main/resources/parameterValidator/parameter-validator.xml");
			
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
	protected void waitForMessagesInMockEndpoints(int resultSwitchCount, int resultsWarningCount, int resultErrorCount, int resultsParameterCount) throws InterruptedException {
		for (int i = 2; !(resultsSwitch.getReceivedCounter() >= resultSwitchCount
				&& resultsWarning.getReceivedCounter() >= resultsWarningCount
				&& resultsError.getReceivedCounter() >= resultErrorCount && resultsParameters
				.getReceivedCounter() >= resultsParameterCount) && i < 4096; i *= 2) {

			Thread.sleep(i);
		}
	}
}
