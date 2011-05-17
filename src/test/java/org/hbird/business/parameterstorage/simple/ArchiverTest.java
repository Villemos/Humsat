/**
 * Licensed under the Apache License, Version 2.0. You may obtain a copy of 
 * the License at http://www.apache.org/licenses/LICENSE-2.0 or at this project's root.
 */

package org.hbird.business.parameterstorage.simple;

import static org.junit.Assert.*;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/*
 * Tests Hummingbird's 'CreateSqlStatement' Bean
 */
@ContextConfiguration(locations = { "/simple/ArchiverTest-context.xml" })
public class ArchiverTest extends AbstractJUnit4SpringContextTests {

	@Produce(uri = "activemq:queue:Parameters")
	protected ProducerTemplate producer = null;

	@Autowired
	protected CamelContext archiverContext = null;

	@Autowired
	protected DataSource database = null;

	protected JdbcTemplate template = null;

	protected String parameterName = "ELEVATION";
	protected String parameterValue = "987654.3210987654";
	protected String parameterTimestamp = "1301234567891";
	protected String parameterValueType = "class java.lang.Double";
	protected double parameterBody = 987654.3210987654;

	protected String queryForTableCount = "SELECT count(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = '"
			+ parameterName.toUpperCase()
			+ "' OR TABLE_NAME = '"
			+ parameterName.toLowerCase() + "';";
	
	protected String queryForRowCount = "select count(*) from " + parameterName + ";";
	protected String queryForBody = "select BODY from " + parameterName + ";";
	protected String correctBodyAsXMLString = "<?xml version='1.0' encoding='UTF-8'?><double>987654.3210987654</double>";


	@Before
	public void initialize() {
		template = new JdbcTemplate(database);

		template.execute("DROP TABLE IF EXISTS " + parameterName.toUpperCase() + ";");
		template.execute("DROP TABLE IF EXISTS " + parameterName.toLowerCase() + ";");
	}

	/*
	 * Tests the 'DatabaseArchiver' inside a camel route.
	 */
	@Test
	public void testStore() {
		// Prepare exchange. All necessary headers and the body are set.
		Exchange exchange = new DefaultExchange(archiverContext);
		exchange.getIn().setHeader("Name", parameterName);
		exchange.getIn().setHeader("Value", parameterValue);
		exchange.getIn().setHeader("Timestamp", parameterTimestamp);
		exchange.getIn().setHeader("Value Type", parameterValueType);
		exchange.getIn().setBody(parameterBody);

		// Send exchange and wait 0.1sec
		producer.send(exchange);

		// Wait until there is 1 dataset in the database, but max 0.5sec
		for (int i = 4; template.queryForInt(queryForTableCount) != 1 && i < 512; i *= 2) {
			try {Thread.sleep(i);}
			catch (Exception e) {}
		}

		int numberOfTables = template.queryForInt(queryForTableCount);
		assertEquals("Number of tables in Database is not correct,", 1, numberOfTables);
		
		// Wait until there is 1 dataset in the database, but max 0.5sec
		for (int i = 4; template.queryForInt(queryForRowCount) != 1 && i < 512; i *= 2) {
			try {Thread.sleep(i);}
			catch (Exception e) {}
		}
	
		int numberOfDatasets = template.queryForInt(queryForRowCount);
		assertEquals("Number of rows in table is incorrect,", 1, numberOfDatasets);
		
		
		String body = template.queryForObject(queryForBody, String.class);
		assertEquals("Body has not been converted correctly,", correctBodyAsXMLString, body);
		
	}
}
