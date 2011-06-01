/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.parameterstorage.simple;

import static org.junit.Assert.*;
import static org.junit.Assert.fail;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.hbird.exchange.type.Parameter;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/*
 * Tests Hummingbird's 'CreateSqlStatement' Bean
 */
@ContextConfiguration(locations = { "file:src/main/resources/humsat-parameterstorage*.xml"/*,
									"file:src/main/resources/humsat-parameterstorage-retriever.xml"*/ })
public class ArchiverRetrieverTest extends AbstractJUnit4SpringContextTests {
//
//	@Produce(uri = "activemq:topic:Parameters")
//	protected ProducerTemplate produceParameters = null;
//
//	@Produce(uri = "activemq:RetrieverCommands")
//	protected ProducerTemplate produceCommands = null;
//
//	@EndpointInject(uri = "mock:Result")
//	protected MockEndpoint result = null;
//
//	@EndpointInject(uri = "mock:Failed")
//	protected MockEndpoint failed = null;

//	@Autowired
//	protected CamelContext archiverContext = null;
//
//	@Autowired
//	protected CamelContext retrieverContext = null;

//	@Autowired
//	protected DataSource database = null;
//
//	protected JdbcTemplate template = null;
//
//	protected String parameterName = "test_parameter";
//	protected Parameter[] testParameters = new Parameter[4];

	@Before
	public void initialize() {
//		template = new JdbcTemplate(database);

		// Check Database Connection
//		try {
//			database.getConnection();
//		} catch (Exception e) {
//			fail("e.getMessage())");
//		}

		// Prepare database
	//	template.execute("DROP TABLE IF EXISTS " + parameterName.toUpperCase() + ";");
	//	template.execute("DROP TABLE IF EXISTS " + parameterName.toLowerCase() + ";");
/*
		testParameters[0] = new Parameter(parameterName, "test description", 1300001000, 11111, "Java.lang.Int");

		testParameters[1] = new Parameter(parameterName, "test description", 1300002000, 22222, "Java.lang.Int");

		testParameters[2] = new Parameter(parameterName, "test description", 1300003000, 33333, "Java.lang.Int");

		testParameters[3] = new Parameter(parameterName, "test description", 1300004000, 44444, "Java.lang.Int");

		// Add routes to access the retrieved parameter (that are stored in a queue) via the mock
		// component.
		try {
			retrieverContext.addRoutes(new RouteBuilder() {
				public void configure() {
					from("activemq:RetrievedParameters").to("mock:Result");

					from("activemq:RetrieverCommandsFailed").to("mock:Failed");
				}
			});
		} catch (Exception e) {
		}*/
	}

	@Test
	public void testBla() {
		System.out.println("test\n"/* + archiverContext.getRoutes().size()*/);
		
		
	}
	
	/*
	 * Tests the 'DatabaseArchiver'.
	 */
	@Ignore
	@Test
	public void testStoreAndRetrieve() throws InterruptedException {
//		Exchange exchange;
//		for (Parameter p : testParameters) {
//			// Prepare exchange
//			exchange = new DefaultExchange(archiverContext);
//			exchange.getIn().setHeader("Name", p.getName());
//			exchange.getIn().setHeader("Value", p.getValue());
//			exchange.getIn().setHeader("Timestamp", p.getTimestamp());
//			exchange.getIn().setHeader("Value Type", p.getUnit());
//			exchange.getIn().setBody(p);
//
//			// Send exchange
//			produceParameters.send(exchange);
//		}
//		
//		Thread.sleep(3000);
//		
//		result.reset();
//		// Prepare statement
//		String parametersToBeRetrieved = "test_parameter;1300000001500;1300000003500";
//
//		// Prepare exchange (set Body and Headers) and send it.
//		exchange = new DefaultExchange(retrieverContext);
//		exchange.getIn().setBody(parametersToBeRetrieved);
//
//		produceCommands.send(exchange);
//
//		try {
//			// Wait until there are 2 messages in 'result', but 
//			// max 4ms + 8 + 16ms + 32ms + 64ms + 128ms = 252ms
//			for (int i = 4; result.getReceivedCounter() != 2 && i <= 128; i *= 2) {
//				Thread.sleep(i);
//			}
//		} catch (InterruptedException e) {
//		}
//
//		// Execute test
//		assertTrue(result.getReceivedCounter() == 2);
//
//		Message firstMessage = result.getExchanges().get(0).getIn();
//		Message secondMessage = result.getExchanges().get(1).getIn();
//
//		assertEquals("java.lang.int", firstMessage.getBody().getClass().getName());
//		assertEquals(22222, firstMessage.getBody());
//		
//		assertEquals("java.lang.int", secondMessage.getBody().getClass().getName());
//		assertEquals(33333, secondMessage.getBody());		
	}

	@After
	public void tearDown() {
//		template.execute("DROP TABLE IF EXISTS " + parameterName.toUpperCase() + ";");
//		template.execute("DROP TABLE IF EXISTS " + parameterName.toLowerCase() + ";");
	}
}
