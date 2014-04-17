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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Map;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.Encoder;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.impl.llom.OMTextImpl;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAP12Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.util.AXIOMUtils;
import org.wso2.datamapper.engine.core.MappingHandler;
import org.wso2.datamapper.engine.core.MappingResourceLoader;
import org.wso2.datamapper.engine.core.writer.DummyEncoder;
import org.wso2.datamapper.engine.core.writer.WriterRegistry;

public class DataMapperHelper {

	private static final String CACHABLE_DURATION = "cachableDuration";
	private static final String ROOT_TAG = "<text xmlns=\"http://ws.apache.org/commons/ns/payload\"></text>";
	private static final String ENVELOPE = "Envelope";
	private static String cacheDurable = null;
	private static int mappingKey;
	private static long cacheTime;
	private static int time = 10000;

	/* use to define input and output data format */
	public enum DataType {
		CSV("text/csv"), XML("application/xml");
		private final String value;

		private DataType(String value) {
			this.value = value;
		}

		@Override
		public String toString() {
			return value;
		}

		public static DataType fromString(String text) {
			if (text != null) {
				for (DataType b : DataType.values()) {
					if (text.equalsIgnoreCase(b.toString())) {
						return b;
					}
				}
			}
			return null;
		}

	};

	public static void mediateDataMapper(MessageContext context,
			String configkey, String inSchemaKey, String outSchemaKey,
			String inputType, String outputType) throws SynapseException {

		InputStream configFileInputStream = getInputStream(context, configkey);
		InputStream inputSchemaStream = getInputStream(context, inSchemaKey);
		InputStream outputSchemaStream = getInputStream(context, outSchemaKey);

		OMElement inputMessage = context.getEnvelope();

		try {

			SynapseConfiguration synapseConfiguration = context
					.getConfiguration();
			cacheDurable = context.getConfiguration().getRegistry()
					.getConfigurationProperties()
					.getProperty(CACHABLE_DURATION);
			cacheTime = (cacheDurable != null && !cacheDurable.isEmpty()) ? Long
					.parseLong(cacheDurable) : time;

			Map<Integer, Object> mappingResourceMap = synapseConfiguration
					.getdataMappingConfigurationCacheMap();
			MappingResourceLoader mappingResourceLoader = getMappingResources(
					mappingResourceMap, cacheTime, inputSchemaStream,
					outputSchemaStream, configFileInputStream);

			GenericRecord result = MappingHandler.doMap(inputMessage,
					mappingResourceLoader);

			/* use DatumWriters for data type conversion */

			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			DatumWriter<GenericRecord> writer = null;
			Encoder encoder;

			if (outputType != null) {
				switch (DataType.fromString(outputType)) {
				case CSV:
					writer = WriterRegistry.getInstance().get(outputType)
							.newInstance();
					break;
				default:
					// Currently we have csv and xml only, this needs to be
					// properly fixed once we decide on datatypes and default
					// value
					writer = WriterRegistry.getInstance().get(outputType)
							.newInstance();
				}
			} else {
				// if the user didn't mention the outputType currently uses XML
				// as the output type, this needs to be propely fixed
				writer = WriterRegistry.getInstance()
						.get(DataType.XML.toString()).newInstance();
			}

			writer.setSchema(result.getSchema());
			encoder = new DummyEncoder(byteArrayOutputStream);
			writer.write(result, encoder);
			encoder.flush();
			OMElement outmessage = getOutputMessage(
					byteArrayOutputStream.toString(), outputType);

			if (outmessage != null) {
				OMElement firstChild = outmessage.getFirstElement();
				if (firstChild != null) {
					QName resultQName = firstChild.getQName();
					if (resultQName.getLocalPart().equals(ENVELOPE)
							&& (resultQName
									.getNamespaceURI()
									.equals(SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI) || resultQName
									.getNamespaceURI()
									.equals(SOAP12Constants.SOAP_ENVELOPE_NAMESPACE_URI))) {
						SOAPEnvelope soapEnvelope = AXIOMUtils
								.getSOAPEnvFromOM(outmessage.getFirstElement());
						if (soapEnvelope != null) {
							try {
								context.setEnvelope(soapEnvelope);
							} catch (AxisFault axisFault) {
								handleException("Invalid Envelope", axisFault);
							}
						}
					} else {
						context.getEnvelope().getBody()
								.setFirstChild(outmessage);
					}
				} else {
					context.getEnvelope().getBody().setFirstChild(outmessage);
				}
			} else {

			}
		} catch (Exception e) {
			handleException("Mapping failed", e);
		}

	}

	/*
	 * returns the MappingResourceLoader which uses as an input to generate the
	 * final output
	 */
	private static MappingResourceLoader getMappingResources(
			Map<Integer, Object> mappingResourceMap, long cacheTime,
			InputStream inputSchemaStream, InputStream outputSchemaStream,
			InputStream configFileInputStream) throws IOException {

		MappingResourceLoader mappingResourceLoader = null;
		if (mappingResourceMap.isEmpty()) {
			return createNewMappingResourceLoader(mappingResourceMap,
					inputSchemaStream, outputSchemaStream,
					configFileInputStream);
		} else if (mappingResourceMap.containsKey(mappingKey)) {

			CachedMappingResourceLoader cacheContext = (CachedMappingResourceLoader) mappingResourceMap
					.get(mappingKey);
			long cachebleLimit = cacheContext.getDateTime().getTime()
					+ cacheTime;
			if (cachebleLimit >= System.currentTimeMillis()) {
				mappingResourceLoader = cacheContext.getCachedResources();
			} else {
				mappingResourceMap.remove(mappingKey);
				mappingResourceLoader = createNewMappingResourceLoader(
						mappingResourceMap, inputSchemaStream,
						outputSchemaStream, configFileInputStream);
			}

		}
		return mappingResourceLoader;
	}

	/*
	 * creates a new mappingResourceLoader if the resources are not available in
	 * the cache or when the cache expires
	 */
	private static MappingResourceLoader createNewMappingResourceLoader(
			Map<Integer, Object> mappingResourceMap,
			InputStream inputSchemaStream, InputStream outputSchemaStream,
			InputStream configFileInputStream) throws IOException {

		MappingResourceLoader mappingResourceLoader = new MappingResourceLoader(
				inputSchemaStream, outputSchemaStream, configFileInputStream);

		mappingKey= mappingResourceLoader.hashCode();

		mappingResourceMap.put(mappingKey, new CachedMappingResourceLoader(
				Calendar.getInstance().getTime(), mappingResourceLoader));
		return mappingResourceLoader;

	}

	/* Returns the final result as an OMElement */
	private static OMElement getOutputMessage(String result, String outputType)
			throws XMLStreamException {
		OMElement element;
		if (outputType.equals(DataType.CSV.toString())) {
			element = AXIOMUtil.stringToOM(ROOT_TAG);
			element.setText(result);
			return element;
		} else
			return AXIOMUtil.stringToOM(result);
	}

	/* Returns the configuration, input and output schemas as inputStreams */
	private static InputStream getInputStream(MessageContext context, String key) {

		InputStream inputStream = null;
		Object entry = context.getEntry(key);
		if (entry instanceof OMTextImpl) {
			OMTextImpl text = (OMTextImpl) entry;
			String content = text.getText();
			inputStream = new ByteArrayInputStream(content.getBytes());
		}
		return inputStream;
	}

	private static void handleException(String message, Exception e) {
		LogFactory.getLog(DataMapperMediator.class).error(message, e);
		throw new SynapseException(message, e);
	}

}
