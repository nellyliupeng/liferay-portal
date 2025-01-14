/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.petra.salesforce.client.streaming;

import com.liferay.petra.salesforce.client.BaseSalesforceClientImpl;
import com.liferay.petra.string.StringBundler;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.HashMapBuilder;

import com.sforce.soap.partner.PartnerConnection;
import com.sforce.ws.ConnectionException;
import com.sforce.ws.ConnectorConfig;

import java.net.URL;

import java.util.Map;

import org.cometd.bayeux.Channel;
import org.cometd.bayeux.Message;
import org.cometd.bayeux.client.ClientSessionChannel;
import org.cometd.client.BayeuxClient;
import org.cometd.client.transport.ClientTransport;
import org.cometd.client.transport.LongPollingTransport;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;

import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

/**
 * @author Brian Wing Shun Chan
 * @author Rachael Koestartyo
 * @author Peter Shin
 */
public class SalesforceStreamingClientImpl
	extends BaseSalesforceClientImpl implements SalesforceStreamingClient {

	@Override
	public boolean connect() throws ConnectionException {
		if (_bayeuxClient == null) {
			afterPropertiesSet();
		}

		if (_bayeuxClient == null) {
			throw new ConnectionException();
		}

		if (_bayeuxClient.isConnected()) {
			return true;
		}

		_bayeuxClient.handshake();

		boolean connected = _bayeuxClient.waitFor(
			10000, BayeuxClient.State.CONNECTED);

		if (_log.isInfoEnabled()) {
			FormattingTuple formattingTuple = MessageFormatter.format(
				"Connected: {}", connected);

			_log.info(
				formattingTuple.getMessage(), formattingTuple.getThrowable());
		}

		return connected;
	}

	public void destroy() {
		if (_bayeuxClient.isConnected()) {
			boolean disconnected = false;

			while (!disconnected) {
				disconnected = disconnect();
			}
		}

		try {
			_httpClient.stop();
		}
		catch (Exception e) {
			_log.error("Unable to stop http client", e);
		}
	}

	@Override
	public boolean disconnect() {
		if (_bayeuxClient.isDisconnected()) {
			return true;
		}

		_bayeuxClient.disconnect();

		boolean disconnected = _bayeuxClient.waitFor(
			10000, BayeuxClient.State.DISCONNECTED);

		if (_log.isInfoEnabled()) {
			FormattingTuple formattingTuple = MessageFormatter.format(
				"Disconnected: {}", disconnected);

			_log.info(
				formattingTuple.getMessage(), formattingTuple.getThrowable());
		}

		return disconnected;
	}

	@Override
	public Channel getChannel(String name) {
		return _bayeuxClient.getChannel(name);
	}

	@Override
	public int getTransportTimeout() {
		return _transportTimeout;
	}

	@Override
	public void setTransportTimeout(int transportTimeout) {
		_transportTimeout = transportTimeout;
	}

	@Override
	protected void afterPropertiesSet() {
		super.afterPropertiesSet();

		try {
			PartnerConnection partnerConnection = getPartnerConnection();

			ConnectorConfig connectorConfig = partnerConnection.getConfig();

			Map<String, Object> options = HashMapBuilder.<String, Object>put(
				ClientTransport.MAX_NETWORK_DELAY_OPTION,
				_transportTimeout * 6000
			).build();

			_httpClient.start();

			URL url = new URL(connectorConfig.getServiceEndpoint());

			_bayeuxClient = new BayeuxClient(
				StringBundler.concat(
					url.getProtocol(), "://", url.getHost(), "/cometd/37.0"),
				new SalesforceTransport(
					connectorConfig.getSessionId(), options, _httpClient));

			ClientSessionChannel handshakeClientSessionChannel =
				_bayeuxClient.getChannel(Channel.META_HANDSHAKE);

			handshakeClientSessionChannel.addListener(
				new SalesforceMessageListener());

			ClientSessionChannel connectClientSessionChannel =
				_bayeuxClient.getChannel(Channel.META_CONNECT);

			connectClientSessionChannel.addListener(
				new SalesforceMessageListener());

			ClientSessionChannel subscribeClientSessionChannel =
				_bayeuxClient.getChannel(Channel.META_SUBSCRIBE);

			subscribeClientSessionChannel.addListener(
				new SalesforceMessageListener());
		}
		catch (Exception e) {
			_log.error(e.getMessage(), e);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		SalesforceStreamingClientImpl.class);

	private BayeuxClient _bayeuxClient;
	private final HttpClient _httpClient = new HttpClient();
	private int _transportTimeout = 1;

	private class SalesforceMessageListener
		implements ClientSessionChannel.MessageListener {

		@Override
		public void onMessage(
			ClientSessionChannel clientSessionChannel, Message message) {

			if (_log.isInfoEnabled()) {
				FormattingTuple formattingTuple = MessageFormatter.format(
					"Received message: {}", message);

				_log.info(
					formattingTuple.getMessage(),
					formattingTuple.getThrowable());
			}

			if (!message.isSuccessful()) {
				_log.error("Unable to send message");

				if (message.get("error") != null) {
					_log.error((String)message.get("error"));
				}

				if (message.get("exception") != null) {
					Exception e = (Exception)message.get("exception");

					_log.error(e.getMessage(), e);
				}

				_bayeuxClient.disconnect();
			}
		}

	}

	private class SalesforceTransport extends LongPollingTransport {

		public SalesforceTransport(
			String sessionId, Map<String, Object> options,
			HttpClient httpClient) {

			super(options, httpClient);

			_sessionId = sessionId;
		}

		@Override
		protected void customize(Request request) {
			super.customize(request);

			request.header("Authorization", "OAuth " + _sessionId);
		}

		private final String _sessionId;

	}

}