/*
 * The MIT License
 *
 * Copyright 2020 me.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.ahn.rhapsody.ci;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author me_000
 */
public class RhapsodyRestHelper {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RhapsodyRestHelper.class);

    public static CloseableHttpClient getHttpClient(String username, String password) {
        // Basic credentials for REST services
        // TODO: Setup preemptive authentication. See http://hc.apache.org/httpcomponents-client-ga/tutorial/html/authentication.html
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(username, password));

        // Custom SSL context to allow for self-signed certificates
        CloseableHttpClient httpClient;
        CookieStore cookieStore = new BasicCookieStore();
        try {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(10000)
                    .setConnectionRequestTimeout(10000)
                    .setSocketTimeout(10000)
                    .build();
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(KeyStore.getInstance(KeyStore.getDefaultType()), new TrustSelfSignedStrategy())
                    .build();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
            // Create http client with credentials and custom SSL
            httpClient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credsProvider)
                    .setDefaultRequestConfig(config)
                    .setSSLSocketFactory(sslsf)
                    .setDefaultCookieStore(cookieStore)
                    .addInterceptorFirst(new CsrfHttpRequestInterceptor())
                    .addInterceptorLast(new CsrfHttpResponseInterceptor())
                    .build();
        } catch (KeyStoreException | NoSuchAlgorithmException | KeyManagementException ex) {
            LOGGER.info("Exception building the HTTP client", ex);
            throw new RuntimeException(ex);
        }

        return httpClient;
    }
}

class CsrfHttpRequestInterceptor implements HttpRequestInterceptor {

    @Override
    public void process(org.apache.http.HttpRequest request, HttpContext context) throws HttpException, IOException {
        String csrf = CsrfHttpResponseInterceptor.getCsrf();
        if (null != csrf) {
            request.addHeader("X-CSRF-Token", csrf);
        }
    }

}

class CsrfHttpResponseInterceptor implements HttpResponseInterceptor {

    private static String csrf = null;

    @Override
    public void process(HttpResponse response, HttpContext context) throws HttpException, IOException {
        Header csrfHeader = response.getFirstHeader("X-CSRF-Token");
        if (null != csrfHeader) {
            // Set the header for next request
            csrf = csrfHeader.getValue();
        }
    }

    public static String getCsrf() {
        return csrf;
    }

}
