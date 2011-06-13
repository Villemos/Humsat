/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.validation;

import org.hbird.exchange.type.Parameter;
import org.hbird.exchange.type.StateParameter;
import org.junit.Test;

/**
 * Integration test for the validate component to test the 
 * updating of a validation limit.
 * 
 */
public class ValidatorLimitUpdateTest extends ValidatorTest {

	public ValidatorLimitUpdateTest() throws Exception {
		super();
	}

	/**
	 * Limit-update test.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testLimitUpdate() throws Exception {
		// reset all mock endpoints.
		resultsSwitch.reset();
		resultsWarning.reset();
		resultsError.reset();
		resultsParameters.reset();
		
		// Send invalid parameter: 9 Volts is below Humsat's 10 Volts warning-limit.
		Parameter invalidParameter = new Parameter("BATTERY_VOLTAGE", "This is an invalid battery voltage.",
			System.currentTimeMillis(), 9, "Volts");

		producer.sendBodyAndHeader("activemq:topic:Parameters", invalidParameter, "name", "BATTERY_VOLTAGE");
		
		waitForMessagesInMockEndpoints(0, 1, 1, 1);
		System.out.println(resultsWarning.getReceivedCounter());
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
		
		// Delete context because it has been altered by this test. If the ValidatorUpperLowerLimitTest 
		// runs afterwards, it has to re-create the context.
		validatorContext.stop();
		validatorContext = null;
		thisIsTheFirstRun = true;
	}
}
