/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.commanding;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.hbird.business.parameterstorage.InMemoryParameterBuffer;
import org.hbird.exchange.commanding.Argument;
import org.hbird.exchange.commanding.Command;
import org.hbird.exchange.commanding.Task;
import org.hbird.exchange.tasks.DummyTask;
import org.hbird.exchange.type.StateParameter;
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
@ContextConfiguration(locations = { "file:src/main/resources/commandingChain/commanding-chain.xml" })
public class CommandingChainTest extends AbstractJUnit4SpringContextTests {
	@EndpointInject(uri = "mock:ResultsCommands")
	protected MockEndpoint resultsCommands= null;

	@EndpointInject(uri = "mock:ResultsExecutedTasks")
	protected MockEndpoint resultsExecutedTasks = null;

	@Produce(uri = "activemq:queue:Commands")
	protected ProducerTemplate producerQueueCommands = null;
	
	@Produce(uri = "activemq:topic:Parameters")
	protected ProducerTemplate producerTopicParameters = null;

	@Produce(uri = "direct:ParameterRequests")
	protected ProducerTemplate producerDirectParameterRequests = null;
		
	@Autowired
	protected InMemoryParameterBuffer parameterBuffer = null;

	@Autowired
	protected CamelContext commandReleaserContext = null;

	@Before
	public void initialize() throws Exception {
		// Prepare parameter buffer (parameterBuffer)
		parameterBuffer.storeParameter(new StateParameter("TestParameter1", "Description of test parameter 1", null, new Boolean(true)));
		parameterBuffer.storeParameter(new StateParameter("TestParameter2", "Description of test parameter 2", null, new Boolean(true)));
		parameterBuffer.storeParameter(new StateParameter("TestParameter3", "Description of test parameter 3", null, new Boolean(true)));
		
		// Add a route to access activemq:topic:ParametersWarning via a mock endpoint.
		commandReleaserContext.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				from("activemq:queue:ReleasedCommands").to("mock:ResultsCommands");

				from("activemq:queue:ExecutedTasks").to("mock:ResultsExecutedTasks");
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
			newCount = resultsCommands.getReceivedCounter() + resultsExecutedTasks.getReceivedCounter();
		}

		// Reset Mock endpoints so that they don't contain any messages.
		resultsCommands.reset();
		resultsExecutedTasks.reset();
	}

	/**
	 * Tests storage route for the parameter in-memory buffer (id 'cr3') 
	 * Creates a test parameter and stores it in the in-memory buffer. 
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testParameterInMemoryBufferStorage() throws InterruptedException {
		//"activemq:topic:Parameters"
		Exchange exchange = new DefaultExchange(commandReleaserContext);
		exchange.getIn().setBody(new StateParameter("TestParameter4", "Description of test parameter 4", null, new Boolean(true)));
		int parameterBufferParameterCount = parameterBuffer.getLatestParameterValue().size();
		producerTopicParameters.send(exchange);
		
		//Wait max ~4sec until one more message has been stored in the parameter in-memory buffer.
		for (int i = 4; parameterBuffer.getLatestParameterValue().size() < parameterBufferParameterCount + 1 && i < 4096; i *= 2) {
			Thread.sleep(i);
		}

		assertEquals("In-memory parameter buffer contains wrong number of stored parameters.\n", parameterBufferParameterCount + 1, parameterBuffer.getLatestParameterValue().size());
	}

	/**
	 * Tests route for retrieval of parameters from the parameter in-memory buffer. (id 'cr2')
	 * Initiates the retrieval by sending a retrieval request. 
	 */
	@Test
	public void testParameterInMemoryBufferRetrieval() {
		Exchange exchange = new DefaultExchange(commandReleaserContext, ExchangePattern.InOut);
		exchange.getIn().setBody("TestParameter2");
		producerDirectParameterRequests.send(exchange);

		assertEquals("Wrong parameter has been retrieved from in-memory parameter buffer.\n", exchange.getOut().getBody(StateParameter.class).getName(), "TestParameter2");
		assertEquals("Retrieved parameter has wrong value.\n", exchange.getOut().getBody(StateParameter.class).getValue(), true);
	}
	
	/**
	 * Tests command-releaser route (id 'cr1'). 
	 * Creates a test command with lock states and tasks and releases it.
	 * 
	 * @throws InterruptedException
	 */
	//@Test
	public void testCommandingChain() throws InterruptedException {
		// Create test-command
		String name = "Set Transmitter State";
		String description = "Will deploy the payload.";

		List<Argument> arguments = new ArrayList<Argument>();
		List<String> lockStates = Arrays.asList(new String[] { "TestParameter2" });
		List<Task> tasks = Arrays.asList(new Task[] { new DummyTask(), new DummyTask() });

		long releaseTime = 0;
		long executionTime = 0;

		Command test = new Command(name, description, arguments, lockStates, tasks, releaseTime, executionTime);

		// Send test-command 
		producerQueueCommands.sendBody(test);

		// Wait max 4sec until one command is received.
		for (int i = 2; resultsCommands.getReceivedCounter() < 1 && i < 4096; i *= 2) {
			Thread.sleep(i);
		}

		// Check whether test-command has been released successfully.
		assertEquals("Wrong number of commands has been released.", 1, resultsCommands.getReceivedCounter());
		assertEquals(Command.class, resultsCommands.getReceivedExchanges().get(0).getIn().getBody().getClass());

		// Wait max 4sec until 2 tasks are received.
		for (int i = 2; resultsExecutedTasks.getReceivedCounter() < 2 && i < 4096; i *= 2) {
			Thread.sleep(i);
		}

		// Check whether the test-command's tasks have been executed.
		assertEquals("Received command is of a wrong type.", 2, resultsExecutedTasks.getReceivedCounter());

		assertEquals("Type of first received task is incorrect.", DummyTask.class, resultsExecutedTasks.getReceivedExchanges().get(0).getIn().getBody().getClass());
		assertEquals("Execution state of first task is false.", true, resultsExecutedTasks.getReceivedExchanges().get(0).getIn().getBody(DummyTask.class).executeCalled);

		assertEquals("Type of second received task is incorrect.", DummyTask.class, resultsExecutedTasks.getReceivedExchanges().get(1).getIn().getBody().getClass());
		assertEquals("Execution state of second task is false.", true, resultsExecutedTasks.getReceivedExchanges().get(1).getIn().getBody(DummyTask.class).executeCalled);
	}

	
	@After
	public void tearDown() {
	}
}
