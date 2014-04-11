/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.carbon.mediator.datamapper;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.SynapseLog;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.mediators.Value;

/**
 * By using the input schema, output schema and mapping configuration,
 * DataMapperMediator generates the output required by the next mediator for the
 * input received by the previous mediator.
 */
public class DataMapperMediator extends AbstractMediator {

	private Value configurationKey = null;
	private Value inputSchemaKey = null;
	private Value outputSchemaKey = null;
	private String inputType = null;
	private String outputType = null;

	/**
	 * Gets the key which is used to pick the mapping configuration from the
	 * registry
	 * 
	 * @return the key which is used to pick the mapping configuration from the
	 *         registry
	 */
	public Value getConfigurationKey() {
		return configurationKey;
	}

	/**
	 * Sets the registry key in order to pick the mapping configuration
	 * 
	 * @return set the registry key to pick mapping configuration
	 */
	public void setConfigurationKey(Value dataMapperKey) {
		this.configurationKey = dataMapperKey;
	}

	/**
	 * Gets the registry key of the inputSchema
	 */
	public Value getInputSchemaKey() {
		return inputSchemaKey;
	}

	/**
	 * Sets the registry key in order to pick the inputSchema
	 * 
	 * @return set the local registry key to pick inputSchema
	 */

	public void setInputSchemaKey(Value dataMapperKey) {
		this.inputSchemaKey = dataMapperKey;
	}

	/**
	 * Gets the registry key of the outputSchema
	 */
	public Value getOutputSchemaKey() {
		return outputSchemaKey;
	}

	/**
	 * Sets the registry key in order to pick the outputSchema
	 * 
	 * @return set the local registry key to pick outputSchema
	 */
	public void setOutputSchemaKey(Value dataMapperKey) {
		this.outputSchemaKey = dataMapperKey;
	}

	/**
	 * Gets the inputDataType
	 */
	public String getInputType() {
		return inputType;
	}

	/**
	 * Sets the inputDataType
	 */
	public void setInputType(String type) {
		this.inputType = type;
	}

	/**
	 * Gets the outputDataType
	 */
	public String getOutputType() {
		return outputType;
	}

	/**
	 * Sets the outputDataType
	 */

	public void setOutputType(String type) {
		this.outputType = type;
	}

	/**
	 * Get the values from the message context to do the data mapping
	 * 
	 * @param the
	 *            current message for mediation
	 * @return true if mediation happened successfully else false.
	 */
	@Override
	public boolean mediate(MessageContext messageContext) {

		SynapseLog synLog = getLog(messageContext);
		if (synLog.isTraceOrDebugEnabled()) {
			synLog.traceOrDebug("DataMapper mediator : started");
			if (synLog.isTraceTraceEnabled()) {
				synLog.traceTrace("Message :" + messageContext.getEnvelope());
			}
		}

		boolean result = true;
		String configkey = configurationKey.evaluateValue(messageContext);
		String inSchemaKey = inputSchemaKey.evaluateValue(messageContext);
		String outSchemaKey = outputSchemaKey.evaluateValue(messageContext);

		if (!(StringUtils.isNotEmpty(configkey)
				&& StringUtils.isNotEmpty(inSchemaKey) && StringUtils
					.isNotEmpty(outSchemaKey))) {
			LogFactory.getLog(this.getClass()).error(
					"Invalid configurations for the DataMapperMediator");
			result = false;
		}

		try {
			DataMapperHelper.mediateDataMapper(messageContext, configkey,
					inSchemaKey, outSchemaKey, inputType, outputType);
		} catch (SynapseException synExp) {
			LogFactory.getLog(this.getClass()).error(
					"Mediation faild at DataMapperMediator");
			result = false;
		}

		if (synLog.isTraceOrDebugEnabled()) {
			synLog.traceOrDebug("DataMapper mediator : Done");
			if (synLog.isTraceTraceEnabled()) {
				synLog.traceTrace("Message : " + messageContext.getEnvelope());
			}
		}

		return result;
	}

	/**
	 * @return true if the DataMapperMediator is intending to interact with the
	 *         MessageContext
	 */
	@Override
	public boolean isContentAware() {
		return false;
	}

}