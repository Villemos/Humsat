/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.parameterstorage.simple;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import junit.framework.TestCase;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.hbird.exchange.type.Parameter;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;


/**
 * Tests Hummingbird's simple storage component: Parameters will be stored in the database.
 * Afterwards, all functions of the retriever will be run: Restoring all parameters from the
 * database and restoring only a certain number of parameters. Additionally it is tested whether
 * the retriever throws an exception on a faulty control string and whether this exception is
 * correctly caught.
 */
public class ArchiverRetrieverTest extends TestCase {
	protected static boolean thisIsTheFirstRun = true;

	// uri = "activemq:topic:Parameters"
	protected ProducerTemplate archiverProducer = null;

	// uri = "activemq:RetrieverCommands"
	protected ProducerTemplate retrieverProducer = null;

	// uri = "mock:Result"
	protected MockEndpoint result = null;

	// uri = "mock:Failed"
	protected MockEndpoint failed = null;
	
	// the test-data
	protected String parameterName = "test_parameter";
	protected Parameter[] testParameters = {
			new Parameter(parameterName, "test description", 1300001000, 11111,	"Java.lang.Int"),
			new Parameter(parameterName, "test description", 1300002000, 22222,	"Java.lang.Int"),
			new Parameter(parameterName, "test description", 1300003000, 33333,	"Java.lang.Int"),
			new Parameter(parameterName, "test description", 1300004000, 44444,	"Java.lang.Int") };
	
	// retrieverContext needs to be static. The contexts will not get destroyed after each test run,
	// and all tests run in the same context. 
	protected static CamelContext retrieverContext = null; 
	protected CamelContext archiverContext = null;


	/** 
	 * Set up the environment for the test: 
	 * On the first run only, create both contexts, add all necessary routes and prepare and fill the database.
	 * 
	 * On every run, create the mock endpoints, empty the parameters topic (and the corresponding mock endpoints)
	 * so that messages in there don't disturb testing, and create a producer template for the retriever.
	 * 
	 * @throws Exception
	 */
	public void setUp() throws Exception {
		if (thisIsTheFirstRun) {
			//Load contexts
			ApplicationContext temp;

			temp = new FileSystemXmlApplicationContext("file:src/main/resources/parameterStorage/archiver.xml");
			archiverContext = (CamelContext) temp.getBean("archiverContext");
			
			temp = new FileSystemXmlApplicationContext("file:src/main/resources/parameterStorage/retriever.xml");
			retrieverContext = (CamelContext) temp.getBean("retrieverContext");
			
			archiverContext.start();
			retrieverContext.start();
			
			// Add routes that are necessary to run the tests.
			retrieverContext.addRoutes(new RouteBuilder() {
				public void configure() throws Exception {
					from("activemq:RetrievedParameters").to("mock:Results");

					from("activemq:RetrieverCommandsFailed").to("mock:FailedCommands");
				}
			});
		
			// Prepare database
			JdbcTemplate jdbcTemplate = new JdbcTemplate((DataSource) temp.getBean("database"));
			
			jdbcTemplate.execute("DROP TABLE IF EXISTS " + parameterName.toUpperCase() + ";");
			jdbcTemplate.execute("DROP TABLE IF EXISTS " + parameterName.toLowerCase() + ";");
			
			// Store test-parameters in Database
			archiverProducer = archiverContext.createProducerTemplate();
			
			for(Parameter p : testParameters) {
				archiverProducer.sendBody("activemq:topic:Parameters", p);
			}
					
			//Wait two seconds so that the archiver has time to store the parameters. Otherwise, the first 
			//test will fail.
			//TODO implement a nicer solution than 'just wait 2 seconds'
			Thread.sleep(2000);
			
			thisIsTheFirstRun = false;
		}

		// Prepare access to mock components
		result = retrieverContext.getEndpoint("mock:Results",MockEndpoint.class);
		failed = retrieverContext.getEndpoint("mock:FailedCommands", MockEndpoint.class);
		
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
		
		result.reset();
		failed.reset();
		
		// Create producer template for the retriever.
		retrieverProducer = retrieverContext.createProducerTemplate();
	}

	/**
	 * Tests the retrieval of two parameters from the database.
	 * 
	 * @throws InterruptedException
	 */
	
	@Test
	public void testStorageAndRetrievalOfTwoParameters() throws InterruptedException {
		//Issue retrieve-command
		String parametersToBeRetrieved = "test_parameter;1300001500;1300003500";

		retrieverProducer.sendBody("activemq:queue:RetrieverCommands", parametersToBeRetrieved);
		
		//Wait max ~8sec until 2 messages have been received.
		for (int i = 4; result.getReceivedCounter() < 2 && i < 8192; i *= 2) {
			Thread.sleep(i);
		}

		//Assert that the correct parameters have been retrieved from the database.
		assertEquals("Wrong number parameters has been restored from database.", 2, result.getReceivedCounter());
		
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
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testStorageAndRetrievalOfAllParameters() throws InterruptedException {
		//Issue retrieve-command
		String parametersToBeRetrieved = "test_parameter";

		retrieverProducer.sendBody("activemq:queue:RetrieverCommands", parametersToBeRetrieved);
		
		//Wait max ~8sec until 4 messages have been received.
		for (int i = 4; result.getReceivedCounter() < 4 && i < 8192; i *= 2) {
			Thread.sleep(i);
		}

		//Assert that the correct parameters have been retrieved from the database.
		assertEquals("Wrong number parameters has been restored from database.", 4, result.getReceivedCounter());
		
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
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void testWrongRetrieverCommand() throws InterruptedException {
		//Issue retrieve-command
		String parametersToBeRetrieved = "very_wrong:4";

		retrieverProducer.sendBody("activemq:queue:RetrieverCommands", parametersToBeRetrieved);
		
		//Wait max ~8sec until 1 message has been received.
		for (int i = 4; failed.getReceivedCounter() < 1 && i < 8192; i *= 2) {
			Thread.sleep(i);
		}

		//Assert that the correct parameters have been retrieved from the database.
		assertEquals("Error message count is wrong.", 1, failed.getReceivedCounter());
		assertEquals("From database retrieved parameter count is wrong.", 0, result.getReceivedCounter());
	}

	@After
	public void tearDown() {
	}
}
