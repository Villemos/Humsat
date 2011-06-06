/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.commanding;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hbird.exchange.commanding.Argument;
import org.hbird.exchange.commanding.Command;
import org.hbird.exchange.commanding.Task;
import org.hbird.exchange.commanding.actions.SetParameter;
import org.hbird.exchange.type.Parameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * Commanding-chain integration test.
 * 
 */
@ContextConfiguration(locations = { "file:src/main/resources/commandingChain/command-releaser.xml" })
public class CommandReleaserTest extends AbstractJUnit4SpringContextTests {
	@EndpointInject(uri = "mock:ResultsCommands")
	protected MockEndpoint resultsCommands= null;

	@EndpointInject(uri = "mock:ResultsTasks")
	protected MockEndpoint resultsTasks = null;

	@Produce(uri = "activemq:queue:Commands")
	protected ProducerTemplate producer = null;

	@Autowired
	protected CamelContext commandReleaserContext = null;

	@Before
	public void initialize() throws Exception {
		// Add a route to access activemq:topic:ParametersWarning via a mock
		// endpoint.
		commandReleaserContext.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				from("activemq:queue:ReleasedCommands").to("mock:ResultsCommands");

				from("activemq:queue:Tasks").to("mock:ResultsTasks");
			}
		});

		// In case that there are still old parameters left in the parameters
		// topic, wait until all have been routed to the 'results' components, so that
		// they don't disturb the testing.
		int oldCount = -1;
		int newCount = 0;

		while (oldCount < newCount) {
			Thread.sleep(250);
			oldCount = newCount;
			newCount = resultsCommands.getReceivedCounter() + resultsTasks.getReceivedCounter();
		}

		// Reset Mock endpoints so that they don't contain any messages.
		resultsCommands.reset();
		resultsTasks.reset();
	}

	
	@Test
	public void testCommand() throws InterruptedException {
		//Create and send test-command
		String name = "Set Transmitter State";
		String description = "Will deploy the payload.";
		
		List<Argument> arguments = new ArrayList<Argument>();
		List<String> lockStates = new ArrayList<String>();
		List<Task> tasks = new ArrayList<Task>();
		
		Parameter disableCheckValue = new Parameter("State of Deploy Payload Limit Switch", "", false, "State");
		SetParameter disableCheck = new SetParameter("Task to set State of Deploy Payload Limit Switch", "", System.currentTimeMillis(), disableCheckValue);
		tasks.add(disableCheck);
		
		Parameter updateCheckValue = new Parameter("State of Deploy Payload Limit Switch", "", false, "State");
		SetParameter updateCheck = new SetParameter("Task to set State of Deploy Payload Limit", "", System.currentTimeMillis() + 500, updateCheckValue);
		tasks.add(updateCheck);
		
		Parameter enableCheckValue = new Parameter("State of Deploy Payload Limit Switch", "", true, "State");
		SetParameter enableCheck = new SetParameter("Task to set State of Deploy Payload Limit Switch", "", System.currentTimeMillis() + 1000, enableCheckValue);
		tasks.add(enableCheck);
		
		long releaseTime = 0;
		long executionTime = 0;
			
		Command test = new Command(name, description, arguments, lockStates, tasks, releaseTime, executionTime);

		producer.sendBody(test);
		
		//Wait max 4sec until a command is received.
		for (int i = 2; resultsCommands.getReceivedCounter() == 0 && i < 4096; i *= 2) {
			Thread.sleep(1000);
		}

		//TODO not done yet... need to figure out, how to properly configure the commanding chain. The information on the wiki does not seem to be 100% correct.
		assertEquals("Wrong number of commands has been released.", 1, resultsCommands.getReceivedCounter());
		assertEquals("Received command is of a wrong type.", "java.util.ArrayList", resultsCommands.getReceivedExchanges().get(0).getIn().getBody().getClass().getName());
		@SuppressWarnings("unchecked")
		List <Object> commandList = (List<Object>) resultsCommands.getReceivedExchanges().get(0).getIn().getBody();
		assertEquals("Wrong number of tasks (?) in the command has been received.", 4, commandList.size());
	}

	
	@After
	public void tearDown() {
	}
}
