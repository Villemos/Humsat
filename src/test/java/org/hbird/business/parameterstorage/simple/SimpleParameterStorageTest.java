/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.parameterstorage.simple;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hbird.exchange.type.Parameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;


/**
 * Tests Hummingbird's simple storage component: Parameters will be stored in the database.
 * Afterwards, all functions of the retriever will be run: Restoring all parameters from the
 * database and restoring only a certain number of parameters. Additionally it is tested whether
 * the retriever throws an exception on a faulty control string and whether this exception is
 * correctly caught.
 */
@ContextConfiguration(locations = { "file:src/main/resources/parameterStorage/simple-parameter-storage.xml" })
public class SimpleParameterStorageTest extends AbstractJUnit4SpringContextTests {
	private static boolean thisIsTheFirstRun = true;
	
	@Produce(uri = "activemq:topic:Parameters")
	protected ProducerTemplate archiverProducer = null;

	@Produce(uri = "activemq:RetrieverCommands")
	protected ProducerTemplate retrieverProducer = null;

	@EndpointInject(uri = "mock:Results")
	protected MockEndpoint result = null;

	@EndpointInject(uri = "mock:FailedCommands")
	protected MockEndpoint failed = null;
	
	@Autowired
	protected CamelContext storageContext = null;
	
	@Autowired
	protected DataSource database = null;
	
	// the test-data
	protected String parameterName = "test_parameter";
	protected Parameter[] testParameters = {
			new Parameter(parameterName, "test description", 1300001000, 11111,	"Java.lang.Int"),
			new Parameter(parameterName, "test description", 1300002000, 22222,	"Java.lang.Int"),
			new Parameter(parameterName, "test description", 1300003000, 33333,	"Java.lang.Int"),
			new Parameter(parameterName, "test description", 1300004000, 44444,	"Java.lang.Int") };

	/** 
	 * Set up the environment for the test: 
	 * On the first run only, add all necessary routes and prepare and fill the database.
	 * 
	 * On every run, reset the mock endpoints.
	 * 
	 * @throws Exception
	 */
	@Before
	public void initialize() throws Exception {
		if (thisIsTheFirstRun) {
			// Add routes that are necessary to run the tests.
			storageContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:RetrievedParameters").to("mock:Results");

					from("activemq:RetrieverCommandsFailed").to("mock:FailedCommands");
				}
			});
			
			// In case that there are still old parameters left in the parameters topic,
			// wait until all have been routed to the 'result' and 'failed' components, 
			// so that they don't disturb testing.
			int oldCount = -1;
			int newCount = 0;
			
			while (oldCount < newCount) {
				Thread.sleep(250);
				oldCount = newCount;
				newCount = result.getReceivedCounter() + failed.getReceivedCounter();
			}		
		
			// Prepare database
			JdbcTemplate jdbcTemplate = new JdbcTemplate(database);
			
			jdbcTemplate.execute("DROP TABLE IF EXISTS " + parameterName.toUpperCase() + ";");
			jdbcTemplate.execute("DROP TABLE IF EXISTS " + parameterName.toLowerCase() + ";");
			
			// Store test-parameters in Database. Wait a second after sending them to the archiver
			// so that it has time to store them.

			for (Parameter p : testParameters) {
				archiverProducer.sendBody(p);
			}
			Thread.sleep(1000);
			
			thisIsTheFirstRun = false;
		}
		
		result.reset();
		failed.reset();
	}

	/**
	 * Tests the retrieval of two parameters from the database.
	 * Uses route id '2' and '3'.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testStorageAndRetrievalOfTwoParameters() throws InterruptedException {
		// Issue retrieve-command
		String parametersToBeRetrieved = "test_parameter;1300001500;1300003500";

		retrieverProducer.sendBody("activemq:queue:RetrieverCommands", parametersToBeRetrieved);
		
		// Wait max ~8sec until 2 messages have been received.
		for (int i = 4; result.getReceivedCounter() < 2 && i < 8192; i *= 2) {
			Thread.sleep(i);
		}

		// Assert that the correct parameters have been retrieved from the database.
		assertEquals("Wrong number of parameters has been restored from database.", 2, result.getReceivedCounter());
		
		Map<Long,Parameter> receivedParameters = new HashMap<Long,Parameter>();
		for(Exchange e : result.getReceivedExchanges()) {
			Parameter p = e.getIn().getBody(Parameter.class);
			receivedParameters.put(p.getTimestamp(), p);
		}
		
		assertEquals("The first retrieved Parameter has a faulty value.", testParameters[1].getValue(), receivedParameters.get(testParameters[1].getTimestamp()).getValue());
		assertEquals("The second retrieved Parameter has a faulty value.", testParameters[2].getValue(), receivedParameters.get(testParameters[2].getTimestamp()).getValue());
		assertEquals("There should not appear a message in the error queue.", 0, failed.getReceivedCounter());
	}

	/**
	 * Tests the retrieval of all parameters from the database.
	 * Uses route id '2' and '3'.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testStorageAndRetrievalOfAllParameters() throws InterruptedException {
		// Issue retrieve-command
		String parametersToBeRetrieved = "test_parameter";

		retrieverProducer.sendBody("activemq:queue:RetrieverCommands", parametersToBeRetrieved);
		
		// Wait max ~8sec until 4 messages have been received.
		for (int i = 4; result.getReceivedCounter() < 4 && i < 8192; i *= 2) {
			Thread.sleep(i);
		}

		// Assert that the correct parameters have been retrieved from the database.
		assertEquals("Wrong number of parameters has been restored from database.", 4, result.getReceivedCounter());
		
		Map<Long,Parameter> receivedParameters = new HashMap<Long,Parameter>();
		for(Exchange e : result.getReceivedExchanges()) {
			Parameter p = e.getIn().getBody(Parameter.class);
			receivedParameters.put(p.getTimestamp(), p);
		}
		
		assertEquals("The first retrieved Parameter has a faulty value.", testParameters[0].getValue(), receivedParameters.get(testParameters[0].getTimestamp()).getValue());
		assertEquals("The second retrieved Parameter has a faulty value.", testParameters[1].getValue(), receivedParameters.get(testParameters[1].getTimestamp()).getValue());
		assertEquals("The third retrieved Parameter has a faulty value.", testParameters[2].getValue(), receivedParameters.get(testParameters[2].getTimestamp()).getValue());
		assertEquals("The fourth retrieved Parameter has a faulty value.", testParameters[3].getValue(), receivedParameters.get(testParameters[3].getTimestamp()).getValue());
		
		assertEquals("There should not appear a message in the error queue.", 0, failed.getReceivedCounter());
	}
	
	/**
	 * Tests whether an exception is thrown (and correctly caught) on a faulty control string.
	 * Uses route id '2' and '3'.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testWrongRetrieverCommand() throws InterruptedException {
		// Issue retrieve-command
		String parametersToBeRetrieved = "very_wrong:4";

		retrieverProducer.sendBody("activemq:queue:RetrieverCommands", parametersToBeRetrieved);
		
		// Wait max ~8sec until 1 message has been received.
		for (int i = 4; failed.getReceivedCounter() < 1 && i < 8192; i *= 2) {
			Thread.sleep(i);
		}

		// Assert that the correct parameters have been retrieved from the database.
		assertEquals("Error message count is wrong.", 1, failed.getReceivedCounter());
		assertEquals("From database retrieved parameter count is wrong.", 0, result.getReceivedCounter());
	}

	@After
	public void tearDown() {
	}
}
