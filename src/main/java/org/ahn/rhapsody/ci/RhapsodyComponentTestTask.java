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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.ahn.rhapsody.ci.model.Component;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author me
 */
public class RhapsodyComponentTestTask implements Callable<Map> {

    private final static Logger LOGGER = LoggerFactory.getLogger(RhapsodyComponentTestTask.class);

    final Component component;
    final HttpClient httpClient;
    final ObjectMapper mapper;
    final String restUrl;
    final ScheduledExecutorService executor;

    // How long to wait for test to complete
    final Duration waitToComplete = Duration.ofSeconds(5);
    final Duration checkFrequency = Duration.ofMillis(200);

    public RhapsodyComponentTestTask(Component component, String restUrl, HttpClient httpClient, ObjectMapper mapper, ScheduledExecutorService executor) {
        this.component = component;
        this.restUrl = restUrl;
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.executor = executor;
    }

    @Override
    public Map call() throws Exception {
        HttpPost testRequest = new HttpPost(restUrl + "/api/test/" + component.getId());
        
        HttpResponse testResponse = httpClient.execute(testRequest);
        LOGGER.debug("Submitted request to test {} component", component);
        
        if (testResponse.getStatusLine().getStatusCode() != 202) {
            EntityUtils.consume(testResponse.getEntity());
            throw new Exception("Unexpected test response status: " + testResponse.getStatusLine().getReasonPhrase());
        }
        
        URI statusUri = URI.create(testResponse.getFirstHeader("Location").getValue());
        long currentWait = waitToComplete.toMillis();
        ScheduledFuture<Map> future;
        do {
            future = executor.schedule(new StatusCheck(statusUri), checkFrequency.toMillis(), TimeUnit.MILLISECONDS);
            LOGGER.debug("Waiting to complete tests for {}", component);

            try {
                // Wait for the status to be returned: schedule delay + runtime
                Map status = future.get(checkFrequency.toMillis() + (checkFrequency.toMillis() / 2), TimeUnit.MILLISECONDS);
                if (status.containsKey("state") && status.get("state").toString().equals("COMPLETED")) {
                    LOGGER.debug("Component testing is complete for {}", component);
                    // Test has completed, return
                    return status;
                }
            } catch (TimeoutException ex) {
                // Reached timeout, status is still being requested. Will reschedule.
                future.cancel(true);
            } catch (Exception ex) {
                LOGGER.error("Exception while testing {} component", component, ex);
                // Test failed, re-throw
                throw ex;
            } finally {
                // Make sure to cancel
                future.cancel(true);
            }
            // Decrease wait time, will reschedule
            currentWait -= checkFrequency.toMillis();
        } while (currentWait > 0);

        throw new Exception("Test timeout exception");
    }

    class StatusCheck implements Callable<Map> {

        final URI statusUri;

        public StatusCheck(URI statusUri) {
            this.statusUri = statusUri;
        }

        @Override
        public Map call() throws Exception {
            LOGGER.trace("Checking test execution status on component {}", component);
            
            HttpGet statusRequest = new HttpGet(statusUri);
            HttpResponse statusResponse = httpClient.execute(statusRequest);
            if (statusResponse.getStatusLine().getStatusCode() != 200) {
                // Consume the entity to release the connection
                EntityUtils.consume(statusResponse.getEntity());
                throw new Exception("Unexpected status response: " + statusResponse.getStatusLine().getReasonPhrase());
            }
            
            Map status = mapper.readValue(statusResponse.getEntity().getContent(), Map.class);
            return status;
        }

    }

}
