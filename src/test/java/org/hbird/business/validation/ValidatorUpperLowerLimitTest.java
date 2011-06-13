/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.validation;

import java.util.ArrayList;
import java.util.Collection;

import org.hbird.exchange.type.Parameter;
import org.hbird.exchange.type.StateParameter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Integration test for the validate component (upper/lower limit).
 * 
 */
@RunWith(Parameterized.class)
public class ValidatorUpperLowerLimitTest extends  ValidatorTest {
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
	public ValidatorUpperLowerLimitTest(int expectedSwitch, int expectedWarning, int expectedError, int expectedParameter, boolean warningState, boolean errorState, String name, int value) throws Exception {
		super();
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
}
