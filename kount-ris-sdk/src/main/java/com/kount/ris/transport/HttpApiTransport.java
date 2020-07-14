package com.kount.ris.transport;

import java.io.*;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kount.ris.util.RisTransportException;

/**
 * RIS http data transport class.
 * </p>
 * Works with JWT (JSON Web Token) authentication, following the RFC 7519
 * standard. The used key is set as connection header with name
 * 'X-Kount-Api-Key'.
 *
 * @author Kount &lt;custserv@kount.com&gt;
 * @version $Id$
 * @copyright 2010 Keynetics Inc
 */
public class HttpApiTransport extends Transport {

	public static final String CUSTOM_HEADER_MERCHANT_ID = "X-Kount-Merc-Id";
	public static final String CUSTOM_HEADER_API_KEY = "X-Kount-Api-Key";

	/**
	 * Logger.
	 */
	private static final Logger logger = LogManager.getLogger(HttpApiTransport.class);

	/**
	 * SSL socket factory.
	 */
	protected SSLSocketFactory factory = null;

	/**
	 * Cache the api key (minimize file reads to once per instatiation).
	 */
	protected String apiKey;

	protected int connPool ;

	protected int concurrentConn ;

	/**
	 * Default transport constructor.
	 */
	public HttpApiTransport() {
		// Do nothing
	}

	/**
	 * Constructor that accepts a RIS url and an api key as input.
	 * 
	 * @param url RIS server url.
	 * @param key API key.
	 */
	public HttpApiTransport(URL url, String key) {
		setRisServerUrl(url.toString());
		setApiKey(key);
	}

	/**
	 * Set API Key.
	 * 
	 * @param key String Kount Api Key (public) to use for authentication with RIS
	 *            server.
	 */
	public void setApiKey(String key) {
		apiKey = key;
	}

	/**
	 * Set max Connection Pool and max connection per Route.
	 *
	 * @param params Set max Connection Pool and max connection per Route with RIS
	 *            server.
	 */
	public void setConnectionPool(Map<String, String> params){
		connPool = (params.get("CONNPOOL") == null) ? 100 : (Integer.parseInt(params.get("CONNPOOL")));
		concurrentConn = (params.get("CONCURRENTCONN") == null) ? 20 : (Integer.parseInt(params.get("CONCURRENTCONN")));
	}

	/**
	 * Send transaction data to RIS.
	 * 
	 * @throws RisTransportException RIS transport exception
	 * @param params Map of data to send
	 * @return Reader for character stream returned by RIS
	 */
	public Reader send(Map<String, String> params) throws RisTransportException {
		if (!params.containsKey("PTOK") || ("KHASH".equals(params.get("PENC")) && null == params.get("PTOK"))) {
			params.put("PENC", "");
		}

		Reader reader = null;
		try {

			setConnectionPool(params);
			// Creating the Client Connection Pool Manager by instantiating the
			// PoolingHttpClientConnectionManager class.
			PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();

			// Set the maximum number of connections in the pool
			connManager.setMaxTotal(connPool);

			//Set the maximum number of concurrent connections per route
			connManager.setDefaultMaxPerRoute(concurrentConn);

			// Create a ClientBuilder Object by setting the connection manager
			HttpClientBuilder clientbuilder = HttpClients.custom().setConnectionManager(connManager);

			CloseableHttpClient httpClient = clientbuilder.build(); //HttpClients.createDefault();

			HttpPost httpPost = new HttpPost(this.risServerUrl);

			httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
			httpPost.addHeader(CUSTOM_HEADER_API_KEY, this.apiKey);
			httpPost.addHeader(CUSTOM_HEADER_MERCHANT_ID, params.get("MERC"));
			httpPost.setEntity(new UrlEncodedFormEntity(convertToNameValuePair(params)));

			CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
			HttpEntity entity = httpResponse.getEntity();
			reader =  new BufferedReader(new InputStreamReader(entity.getContent()));

			long startTime = System.currentTimeMillis();
			long elapsed = (System.currentTimeMillis() - startTime);

			if (logger.isDebugEnabled()) {
				StringBuilder builder = new StringBuilder();
				builder.append("MERC = ").append(params.get("MERC"));
				builder.append(" SESS = ").append(params.get("SESS"));
				builder.append(" elapsed = ").append(elapsed).append(" ms.");

				logger.debug(builder.toString());
			}
		} catch (Exception ioe) {
			logger.error("Error fetching RIS response", ioe);
			throw new RisTransportException("An error ocurred while getting the RIS response", ioe);
		}

		return reader;
	}

	private SSLSocketFactory getSslSocketFactory() throws RisTransportException {
		if (this.factory != null) {
			return this.factory;
		}

		SSLContext ctx;
		try {
			ctx = SSLContext.getInstance("TLSv1.2");
		} catch (NoSuchAlgorithmException nsae) {
			logger.error("Unable to create SSLContext of type TLSv1.2", nsae);
			throw new RisTransportException("Unable to create SSLContext of type TLSv1.2", nsae);
		}

		try {
			ctx.init(null, null, null);
		} catch (KeyManagementException kme) {
			logger.error("Unable to initialize SSLContext", kme);
			throw new RisTransportException("Unable to initialize SSLContext", kme);
		}

		this.factory = ctx.getSocketFactory();
		return this.factory;
	}
}
