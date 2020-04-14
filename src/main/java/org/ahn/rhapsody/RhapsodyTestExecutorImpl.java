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
package org.ahn.rhapsody;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.ahn.rhapsody.ci.json.TestCase;
import org.ahn.rhapsody.ci.json.TestComponent;
import org.ahn.rhapsody.ci.model.Component;
import org.ahn.rhapsody.ci.model.Filter;
import org.ahn.rhapsody.ci.model.Route;
import org.apache.http.Header;
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
public class RhapsodyTestExecutorImpl implements RhapsodyTestExecutor, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RhapsodyTestExecutorImpl.class);
    private static final Duration testTimeout = Duration.ofSeconds(5);
    private static final Duration checkFrequency = Duration.ofMillis(200);
    private final ScheduledExecutorService executorService;

    public RhapsodyTestExecutorImpl() {
        executorService = Executors.newScheduledThreadPool(1);
    }

    @Override
    public synchronized TestComponent executeTests(Component component, String baseUrl, HttpClient client, ObjectMapper mapper, Logger logger) {
        // Initiate the testing
        HttpPost testRequest = new HttpPost(baseUrl + "/api/test/" + component.getId());

        // All tests roll-up to Route as a parent component
        Route parentRoute;
        if (component instanceof Filter) {
            parentRoute = ((Filter) component).getRoute();
        } else {
            parentRoute = (Route) component;
        }
        TestComponent testComponent = new TestComponent(parentRoute.getId(), parentRoute.getName(), parentRoute.getFolder());

        try {
            // Send the request to initiate testing
            HttpResponse testResponse = client.execute(testRequest);
            logger.info("Submitted request to test {} component", component);

            if (testResponse.getStatusLine().getStatusCode() != 202) {
                EntityUtils.consume(testResponse.getEntity());
                testComponent.setError("Unexpected response status");
                logger.error("Unexpected response status for testing request: " + testResponse.getStatusLine().getReasonPhrase());
                return testComponent;
            }

            // The tests are executed async
            // Need to periodically request status
            URI statusUri = URI.create(testResponse.getFirstHeader("Location").getValue());
            long currentWait = testTimeout.toMillis();
            ScheduledFuture<Map> future;
            do {
                logger.info("Waiting for tests to complete...");
                future = executorService.schedule(new StatusCheck(statusUri, client, mapper), checkFrequency.toMillis(), TimeUnit.MILLISECONDS);

                try {
                    // Wait for the status to be returned: schedule delay + runtime
                    Map status = future.get(checkFrequency.toMillis() + (checkFrequency.toMillis() / 2), TimeUnit.MILLISECONDS);
                    if (status.containsKey("state") && status.get("state").toString().equals("COMPLETED")) {
                        logger.info("Completed testing for {}", component);
                        // Test has completed, return
                        interpretStatus(component, testComponent, status, logger);
                        break; // done with testing
                    }
                } catch (TimeoutException | InterruptedException ex) {
                    // Reached timeout, status is still being requested. Will reschedule.
                    future.cancel(true);
                } catch (ExecutionException ex) {
                    // Execution exception from checking the status
                    testComponent.setError("Exception requesting test status");
                    logger.error("Exception requesting test execution status for {}", component, ex);
                    break; // will not be rescheduling
                } finally {
                    // Make sure to cancel
                    future.cancel(true);
                }

                // Decrease wait time, will reschedule
                currentWait -= checkFrequency.toMillis();
            } while (currentWait > 0);

        } catch (IOException ex) {
            testComponent.setError("Exception: " + ex.getMessage());
            logger.error("Exception requesting test execution from Rhapsody", ex);
        }

        return testComponent;
    }

    protected void interpretStatus(Component component, TestComponent testComponent, Map status, Logger logger) {
        // Check the filter tests
        List results = (List) status.get("results");
        List<TestCase> cases = new ArrayList<>();
        // For each result, generate a JUnit test
        logger.info(results.size() + " test results returned for " + component.toString());
        for (Object resultObj : results) {
            // Each result may indicate testing on a single filter
            // Each connector test is a separate component
            Map result = (Map) resultObj;
            int totalCount = result.containsKey("totalCount") ? Integer.valueOf(result.get("totalCount").toString()) : 0;
            int passedCount = result.containsKey("passedCount") ? Integer.valueOf(result.get("passedCount").toString()) : 0;
            int failedCount = result.containsKey("failedCount") ? Integer.valueOf(result.get("failedCount").toString()) : 0;
            int executedCount = result.containsKey("executedCount") ? Integer.valueOf(result.get("executedCount").toString()) : 0;
            int skippedCount = result.containsKey("skippedCount") ? Integer.valueOf(result.get("skippedCount").toString()) : 0;
            int errorCount = result.containsKey("errorCount") ? Integer.valueOf(result.get("errorCount").toString()) : 0;

            // Add up the counts of all results
            testComponent.setTotalCount(testComponent.getTotalCount() + totalCount);
            testComponent.setPassedCount(testComponent.getPassedCount() + passedCount);
            testComponent.setFailedCount(testComponent.getFailedCount() + failedCount);
            testComponent.setExecutedCount(testComponent.getExecutedCount() + executedCount);
            testComponent.setSkippedCount(testComponent.getSkippedCount() + skippedCount);
            testComponent.setErrorCount(testComponent.getErrorCount() + errorCount);

            String path = result.get("path").toString();
            String filterName = "";
            if (path != null) {
                String[] parts = path.split("/");
                filterName = parts[parts.length - 1];
            }

            if (result.containsKey("filterTests")) {
                List<Map> filterTests = (List<Map>) result.get("filterTests");
                for (Map filterTest : filterTests) {
                    String testName = filterTest.get("testName").toString();
                    String testDescription = filterTest.get("testDescription").toString();
                    String testResult = filterTest.get("result").toString();

                    TestCase testCase = new TestCase(testName, testDescription, testResult);
                    testCase.setFilterName(filterName);
                    cases.add(testCase);
                }
            }

            if (result.containsKey("connectorTests")) {
                List<Map> connectorTests = (List<Map>) result.get("connectorTests");
                for (Map connectorTest : connectorTests) {
                    String testName = connectorTest.get("testName").toString();
                    String testDescription = connectorTest.get("testDescription").toString();
                    String testResult = connectorTest.get("result").toString();
                    String connectorName = connectorTest.get("connectorName").toString();

                    TestCase testCase = new TestCase(testName, testDescription, testResult);
                    testCase.setConnectorName(connectorName);
                    cases.add(testCase);
                }
            }
        }

        // Add the test cases
        cases.forEach(testComponent::addTest);
    }

    @Override
    public void close() throws IOException {
        if (executorService != null) {
            List<Runnable> leftover = executorService.shutdownNow();
            if (!leftover.isEmpty()) {
                LOGGER.warn("Executor terminated with {} awaiting tasks", leftover.size());
            }
        }
    }

    class StatusCheck implements Callable<Map> {

        URI statusUri;
        HttpClient client;
        ObjectMapper mapper;

        public StatusCheck(URI statusUri, HttpClient client, ObjectMapper mapper) {
            this.statusUri = statusUri;
            this.client = client;
            this.mapper = mapper;
        }

        @Override
        public Map call() throws Exception {
            LOGGER.trace("Checking test execution status on component at {}", statusUri);

            HttpGet statusRequest = new HttpGet(statusUri);
            HttpResponse statusResponse = client.execute(statusRequest);
            if (statusResponse.getStatusLine().getStatusCode() != 200) {
                Header contentTypeHeader = statusResponse.getFirstHeader("Content-Type");
                Header contentLengthHeader = statusResponse.getFirstHeader("Content-Length");
                
                // Check if we can parse the response for a better error description
                if (contentTypeHeader != null 
                        && contentLengthHeader != null 
                        && Integer.parseInt(contentLengthHeader.getValue()) > 0 
                        && contentTypeHeader.getValue().contains("json")) {
                    // Additional information is available
                    Map status = mapper.readValue(statusResponse.getEntity().getContent(), Map.class);
                    Map error = (Map) status.get("error");
                    List messages = (List) error.get("messages");
                    if (messages != null && !messages.isEmpty()) {
                        throw new Exception("Error: " + messages.get(0));
                    }
                }
                
                // If no content is available, throw a generic exception
                EntityUtils.consume(statusResponse.getEntity());
                throw new Exception("Unexpected status response: " + statusResponse.getStatusLine().getReasonPhrase());
            }
            
            // Normal response
            Map status = mapper.readValue(statusResponse.getEntity().getContent(), Map.class);
            return status;
        }

    }
}
