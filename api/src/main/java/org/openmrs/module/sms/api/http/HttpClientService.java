/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 *  v. 2.0. If a copy of the MPL was not distributed with this file, You can
 *  obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 *  the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 *
 */

package org.openmrs.module.sms.api.http;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.openmrs.module.sms.api.configs.Config;

import java.util.HashMap;
import java.util.Map;

public class HttpClientService {

  /** Give up if the TCP connection cannot be established in this long. */
  private static final int CONNECT_TIMEOUT_MS = 10000;

  /** Give up if the provider does not answer in this long. */
  private static final int SOCKET_TIMEOUT_MS = 30000;

  private final HttpClient commonHttpClient;
  private final Map<String, HttpClient> smsProviderHttpClients = new HashMap<>();

  HttpClientService(HttpClient commonHttpClient) {
    this.commonHttpClient = commonHttpClient;
  }

  public HttpClientService() {
    this(createDefaultHttpClient());
  }

  private static HttpConnectionManagerParams timeoutParams() {
    HttpConnectionManagerParams params = new HttpConnectionManagerParams();
    params.setConnectionTimeout(CONNECT_TIMEOUT_MS);
    params.setSoTimeout(SOCKET_TIMEOUT_MS);
    return params;
  }

  /** Shared client for internal calls. Pools connections and is thread safe. */
  private static HttpClient createDefaultHttpClient() {
    MultiThreadedHttpConnectionManager manager = new MultiThreadedHttpConnectionManager();
    manager.setParams(timeoutParams());
    return new HttpClient(manager);
  }

  /**
   * Client used to talk to an SMS provider. Deliberately does not pool: every send opens a fresh
   * connection.
   *
   * <p>A pooled connection stays open for the life of the JVM, and nothing notices when the far end
   * goes away — the provider's load balancer moves to another address, or the network silently drops
   * an idle flow. The socket still reads as ESTABLISHED, the pool hands it back, and the send blocks
   * until the kernel gives up, which takes roughly 16 minutes. Sends are serialised, so the whole
   * queue stalls behind it.
   *
   * <p>NOTE: {@link SimpleHttpConnectionManager} is not thread safe. This is only correct because
   * {@code SmsHttpService.send()} is {@code synchronized}. Do not remove that keyword.
   */
  private static HttpClient createNonPoolingHttpClient() {
    SimpleHttpConnectionManager manager = new SimpleHttpConnectionManager(true);
    manager.setParams(timeoutParams());
    return new HttpClient(manager);
  }

  public HttpClient getCommonHttpClient() {
    return commonHttpClient;
  }

  public HttpClient getProviderHttpClient(Config config) {
    return smsProviderHttpClients.computeIfAbsent(
        config.getName(), key -> createNonPoolingHttpClient());
  }
}
