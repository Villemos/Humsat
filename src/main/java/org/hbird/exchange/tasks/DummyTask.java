/**
 * Licensed to the Hummingbird Foundation (HF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The HF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hbird.exchange.tasks;

import org.hbird.exchange.commanding.Task;


/**
 * An implementation of the Task for test purposes. Do nothing, besides
 * recording which methods have been called.
 *
 */
public class DummyTask implements Task {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public long deltaTime = 0;
	
	public boolean executeCalled = false;
	public boolean setValueCalled = false;
	
	public String objectid;
		
	@Override
	public Object execute() {
		executeCalled = true;
		return null;
	}

	@Override
	public String getObjectid() {
		return objectid;
	}

	@Override
	public long getExecutionDelay() {
		return 0;
	}
}
