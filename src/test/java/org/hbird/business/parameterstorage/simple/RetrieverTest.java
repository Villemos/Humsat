/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.parameterstorage.simple;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Statement;

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
import org.apache.camel.test.CamelTestSupport;
import org.hbird.business.parameterstorage.simple.AddTimestampToBody;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/*
 * Tests Hummingbird's 'Retriever' Bean
 */
@ContextConfiguration(locations = { "/simple/RetrieverTest-context.xml" })
public class RetrieverTest extends AbstractJUnit4SpringContextTests {
	@Produce(uri = "activemq:RetrieverCommands")
	protected ProducerTemplate producer = null;

	@EndpointInject(uri = "mock:Result")
	protected MockEndpoint result = null;
	
	@EndpointInject(uri = "mock:Failed")
	protected MockEndpoint failed = null;

	@Autowired
	protected CamelContext context = null;

	@Autowired
	protected DataSource database = null;

	/*
	 * Create the database and fill it with 4 datasets. Each Dataset has a
	 * different timestamp.
	 */
	@Before
	public void initialize() {
		try {
			Statement statement = database.getConnection().createStatement();
			statement.execute("DROP TABLE IF EXISTS test_parameter;");
			statement
					.execute("CREATE TABLE test_parameter (timestamp BIGINT, value DOUBLE, "
							+ "local_timestamp BIGINT, Body varchar(500), PRIMARY KEY (timestamp));");
			statement
					.execute("INSERT INTO test_parameter (timestamp, local_timestamp, body) "
							+ "values ('1300000001000', '1301910090001', '<long>11111</long>');");
			statement
					.execute("INSERT INTO test_parameter (timestamp, local_timestamp, body) "
							+ "values ('1300000002000', '1301910090002', '<long>22222</long>');");
			statement
					.execute("INSERT INTO test_parameter (timestamp, local_timestamp, body) "
							+ "values ('1300000003000', '1301910090003', '<long>33333</long>');");
			statement
					.execute("INSERT INTO test_parameter (timestamp, local_timestamp, body) "
							+ "values ('1300000004000', '1301910090004', '<long>44444</long>');");
		} catch (SQLException se) {
			se.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		//Add routes to access the retrieved parameter that are stored in a queue, via the mock component.
		try{
		context.addRoutes(
			new RouteBuilder() {
				public void configure() {
					from("activemq:RetrievedParameters").to("mock:Result");
				}
			}
		);
		context.addRoutes(new RouteBuilder() {
			public void configure() {
				from("activemq:RetrieverCommandsFailed").to("mock:Failed");
				}
			}
		);
		} catch(Exception e) {}
	}
				
	/*
	 * Drop the database
	 */
	@After
	public void dropDatabase() throws Exception {
		Statement statement = database.getConnection().createStatement();
		statement.execute("DROP TABLE test_parameter");
	}

	/*
	 * Test-method: Creates a query to retrieve 2 of the 4 datasets 
	 * stored in the test-database. Tests  if the correct datasets are retrieved.
	 */
	@Test
	public void fetchAllParameters() {
		result.reset();
		// Prepare statement
		String sqlQuery = "test_parameter";

		// Prepare exchange (set Body and Headers) and send it.
		Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(sqlQuery);

		producer.send(exchange);

		try {
			// Wait until there are 4 messages in 'result', but max ~2sec
			for(int i = 4; result.getReceivedCounter() != 4 && i < 2048; i *= 2) {
				Thread.sleep( i );
			}
		} catch (InterruptedException e) {
		}

		// Execute test
		assertEquals(4, result.getReceivedCounter());

		Message Message1 = result.getExchanges().get(0).getIn();
		Message Message2 = result.getExchanges().get(1).getIn();
		Message Message3 = result.getExchanges().get(2).getIn();
		Message Message4 = result.getExchanges().get(3).getIn();

		assertEquals((long)11111, Message1.getBody());
		assertEquals((long)22222, Message2.getBody());
		assertEquals((long)33333, Message3.getBody());
		assertEquals((long)44444, Message4.getBody());
	}
	
	/*
	 * Test-method: Creates a query to retrieve all 4 datasets stored in the 
	 * test-database. Tests if the correct datasets are retrieved.
	 */
	@Test
	public void fetchTwoParameters() {
		result.reset();
		// Prepare statement
		String sqlQuery = "test_parameter;1300000001500;1300000003500";

		// Prepare exchange (set Body and Headers) and send it.
		Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(sqlQuery);

		producer.send(exchange);

		try {
			// Wait until there are 2 messages in 'result', but max 4ms + 8 + 16ms + 32ms + 64ms + 128ms = 252ms
			for(int i = 4; result.getReceivedCounter() != 2 && i <= 128; i *= 2) {
				Thread.sleep( i );
			}
		} catch (InterruptedException e) {
		}

		// Execute test
		assertTrue(result.getReceivedCounter() == 2);

		Message firstMessage = result.getExchanges().get(0).getIn();
		Message secondMessage = result.getExchanges().get(1).getIn();

		assertEquals("java.lang.Long", firstMessage.getBody().getClass().getName());
		assertEquals((long)22222, firstMessage.getBody());
		
		assertEquals("java.lang.Long", secondMessage.getBody().getClass().getName());
		assertEquals((long)33333, secondMessage.getBody());
	}
	
	/*
	 * Test-method: Creates a query that should throw a IOException.
	 */
	@Test 
	public void fetchParametersInvalidInput() {
		result.reset();
		
		// Prepare statement
		String sqlQuery = "very;wrong";
		
		// Prepare exchange (set Body and Headers) and send it.
		Exchange exchange = new DefaultExchange(context);
		exchange.getIn().setBody(sqlQuery);

		producer.send(exchange);
		
		try {
		Thread.sleep(100);
		} catch (InterruptedException e) {};
		
		assertEquals("Number of received exceptions is wrong,", 1, failed.getReceivedCounter());
	}
}
