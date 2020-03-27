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
package org.ahn.rhapsody.ci.build;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ahn.rhapsody.ci.GlobUtils;
import org.ahn.rhapsody.ci.RhapsodyComponentTestTask;
import org.ahn.rhapsody.ci.RhapsodyRestHelper;
import org.ahn.rhapsody.ci.json.TestCase;
import org.ahn.rhapsody.ci.json.TestComponent;
import org.ahn.rhapsody.ci.json.TestSuite;
import org.ahn.rhapsody.ci.model.Component;
import org.ahn.rhapsody.ci.model.Filter;
import org.ahn.rhapsody.ci.model.Route;
import org.ahn.rhapsody.ci.scm.RhapsodySCM;
import org.ahn.rhapsody.ci.scm.RhapsodySCMAction;
import org.apache.http.client.HttpClient;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Builder to execute and report Rhapsody tests
 *
 * @author Sergei Izvorean
 */
public class RhapsodyBuilder extends Builder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RhapsodyBuilder.class);

    private String routePatterns;
    private String filterPatterns;
    private boolean allowEmptyResults = false;

    private transient HttpClient httpClient;
    private transient ObjectMapper objectMapper;

    @DataBoundConstructor
    public RhapsodyBuilder(String routePatterns, String filterPatterns, boolean allowEmptyResults) {
        this.routePatterns = routePatterns;
        this.filterPatterns = filterPatterns;
        this.allowEmptyResults = allowEmptyResults;
    }

    public String getRoutePatterns() {
        return routePatterns;
    }

    public String getFilterPatterns() {
        return filterPatterns;
    }

    public boolean isAllowEmptyResults() {
        return allowEmptyResults;
    }

    @Override
    public Collection<? extends Action> getProjectActions(AbstractProject<?, ?> project) {
        return Arrays.asList(new RhapsodyProjectAction(project), new RhapsodyGraphAction(project));
    }

    /**
     * Get all routes from Rhapsody SCM step
     *
     * @return
     * @throws IOException
     * @throws Exception
     */
    protected List<Route> getAllRoutes(AbstractBuild<?, ?> build) throws Exception {
        ObjectMapper mapper = getObjectMapper();

        // Read the saved file
        FilePath componentsFile = new FilePath(build.getWorkspace(), RhapsodySCM.COMPONENTS_FILENAME);
        Map json = mapper.readValue(componentsFile.read(), Map.class);
        Map data = (Map) json.get("data");

        // Find all routes
        return findAllRoutes(data, new ArrayList<>());
    }

    /**
     * Filter the components needed for testing, based on configured patterns
     *
     * @param allRoutes
     * @param routeFilterPattens
     * @param filterFilterPatterns
     * @return
     */
    protected List<Component> filterComponentsToTest(List<Route> allRoutes, String routeFilterPattens, String filterFilterPatterns) {
        if (routeFilterPattens == null || routeFilterPattens.isEmpty()) {
            throw new IllegalArgumentException("Route filter patterns must not be blank");
        }

        List<Component> componentsToTest = new ArrayList<>();
        String[] routeFilterPattern = routeFilterPattens.split("\n");

        List<Pattern> routeRegex = new ArrayList<>();
        List<Pattern> filterRegex = new ArrayList<>();
        for (String pattern : routeFilterPattern) {
            routeRegex.add(Pattern.compile(GlobUtils.toRegex(pattern), Pattern.CASE_INSENSITIVE));
        }

        if (filterFilterPatterns != null && !filterFilterPatterns.isEmpty()) {
            String[] filterFilterPattern = filterFilterPatterns.split("\n");
            for (String pattern : filterFilterPattern) {
                filterRegex.add(Pattern.compile(GlobUtils.toRegex(pattern), Pattern.CASE_INSENSITIVE));
            }
        }

        // Filter on the name of either route & filter or just route
        // Just route testing allows for connector testing
        allRoutes.forEach(r -> {
            // Match on the route name
            Optional<Matcher> routeMatcher = routeRegex.stream()
                    .map(regex -> regex.matcher(r.getName()))
                    .filter(m -> m.matches())
                    .findAny();
            if (routeMatcher.isPresent()) {
                // Route name is matching, check if are only testing filters on the route
                if (!filterRegex.isEmpty()) {
                    // Check if route's filter name matches, will test
                    for (Filter f : r.getFilters()) {
                        Optional<Matcher> matcher = filterRegex.stream()
                                .map(p -> p.matcher(f.getName()))
                                .filter(m -> m.matches())
                                .findAny();
                        if (matcher.isPresent()) {
                            componentsToTest.add(f);
                        }
                    }
                } else {
                    // Add the whole route to be tester
                    componentsToTest.add(r);
                }
            }
        });

        return componentsToTest;
    }

    /**
     * Execute the test on Rhapsody's component via REST API
     *
     * @param component
     * @param listener
     * @param executorService
     * @return
     * @throws Exception
     */
    protected TestComponent performComponentTest(Component component, BuildListener listener, HttpClient client, String restUrl, ScheduledExecutorService executorService) throws Exception {
        ObjectMapper mapper = getObjectMapper();
        PrintStream stdout = listener.getLogger();

        Route parentRoute;
        if (component instanceof Filter) {
            parentRoute = ((Filter) component).getRoute();
        } else {
            parentRoute = (Route) component;
        }
        TestComponent testComponent = new TestComponent(parentRoute.getId(), parentRoute.getName(), parentRoute.getFolder());

        RhapsodyComponentTestTask task = new RhapsodyComponentTestTask(component, restUrl, client, mapper, executorService);
        stdout.println("Executing the test for '" + component.toString() + "'");
        Map status = task.call();
        // Check the filter tests
        List results = (List) status.get("results");
        List<TestCase> cases = new ArrayList<>();
        // For each result, generate a JUnit test
        stdout.println(results.size() + " test results returned for " + component.toString());
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

        return testComponent;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build,
            Launcher launcher,
            BuildListener listener) throws InterruptedException, IOException {
        PrintStream stdout = listener.getLogger();
        RhapsodySCMAction scmAction = build.getAction(RhapsodySCMAction.class);
        if (scmAction == null) {
            listener.error("Rhapsody SCM must be configured");
            return false;
        }

        String restUrl = scmAction.getRestUrl();

        LOGGER.info("Performing build on Rhapsody instance at {}", restUrl);

        // Run through validation
        if (routePatterns == null) {
            stdout.println("Route patterns cannot be empty");
            return false;
        }

        if (filterPatterns == null) {
            filterPatterns = "";
        }

        // Get credentials
        StandardUsernamePasswordCredentials credentials
                = CredentialsProvider.findCredentialById(scmAction.getCredentialsId(), StandardUsernamePasswordCredentials.class,
                        build, Collections.EMPTY_LIST);
        if (credentials == null) {
            listener.error("Unable to find credentials");
            return false;
        }
        String serviceUsername = credentials.getUsername();

        stdout.println("Performing build on Rhapsody instance at " + restUrl);
        stdout.println("REST URL: " + restUrl);
        stdout.println("Service username: " + serviceUsername);
        stdout.println("Route pattern: ");
        stdout.println(routePatterns);
        stdout.println("Filter pattern: ");
        stdout.println(filterPatterns);
        stdout.println("Allow empty results: " + Boolean.toString(allowEmptyResults));
        stdout.println();

        // Track the access to credentials
        CredentialsProvider.track(build, credentials);

        HttpClient client = RhapsodyRestHelper.getHttpClient(credentials.getUsername(), credentials.getPassword().getPlainText());
        // Get services in-case the builder was de-serialized
        // See: https://javadoc.jenkins-ci.org/hudson/tasks/BuildStep.html
        ObjectMapper mapper = getObjectMapper();

        // Find all components
        List<Route> allRoutes = null;
        List<Component> componentsToTest = new ArrayList<>();
        try {
            allRoutes = getAllRoutes(build);
            componentsToTest = filterComponentsToTest(allRoutes, routePatterns, filterPatterns);
        } catch (Exception ex) {

        }

        if (componentsToTest.isEmpty()) {
            listener.error("Unable to determine components to test. Check configuration.");
            build.setResult(Result.FAILURE);
            return false;
        }

        stdout.println("Will test " + componentsToTest.size() + " component(s) out of " + allRoutes.size() + " total routes");
        stdout.println("");

        // Run through the testing, one test at a time
        // Rhapsody does not support running multiple tests via REST API
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        TestSuite suite = new TestSuite();
        boolean answer = true;
        int testsExecuted = 0;
        int testsFailed = 0;
        int testsSucceeded = 0;
        int testsSkipped = 0;

        for (Component component : componentsToTest) {
            try {
                testsExecuted++;
                long startTime = System.currentTimeMillis();
                TestComponent testComponent = performComponentTest(component, listener, client, scmAction.getRestUrl(), executorService);
                
                long duration = System.currentTimeMillis() - startTime;
                testComponent.setDuration(duration);
                
                // Add to the suite
                suite.addComponent(testComponent);

                // Check if any tests actually executed
                if (testComponent.getTests().isEmpty() && !allowEmptyResults) {
                    // Return failed on no tests
                    listener.error("Empty results are not allowed. Fail.");
                    testsFailed++;
                    answer = false;
                    continue;
                } else if (testComponent.getTests().isEmpty()) {
                    stdout.println("Empty results are allowed. Pass.");
                    testsSkipped++;
                    continue;
                }

                // Evaluate individual test
                if (testComponent.getErrorCount() > 0 || testComponent.getFailedCount() > 0) {
                    // Failed tests
                    listener.error("Failed test result for " + component.toString());
                    testsFailed++;
                    answer = false;
                    continue;
                }

                testsSucceeded++;

            } catch (Exception ex) {
                listener.error("Exception executing tests on component: " + component);
                ex.printStackTrace(stdout);
                // Assume failure
                testsFailed++;

                answer = false;
            } finally {
                stdout.println("");
            }
        }

        // Shutdown the executor
        executorService.shutdownNow();

        // Add the action
        build.addAction(new RhapsodyBuildAction(testsSucceeded, testsFailed, testsSkipped, testsExecuted));

        // Save the report
        File outputFile = new File(build.getRootDir(), "rh-test-suite.json");
        try (OutputStream os = new FileOutputStream(outputFile)) {
            mapper.writeValue(os, suite);
        }

        saveJUnitXml(build, suite);

        // Output stats
        stdout.println("");
        stdout.println(testsExecuted + " executed / " + testsSucceeded + " succeeded / " + testsFailed + " failed / " + testsSkipped + " skipped.");

        LOGGER.info("Build complete on Rhapsody instance at {}", restUrl);
        return answer;
    }

    protected ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }

        return objectMapper;
    }

    /**
     * Get a list of routes from configuration hierarchy
     *
     * @param root
     * @param folderPath
     * @return
     */
    private static List<Route> findAllRoutes(Map root, List<String> folderPath) {
        List<Route> routes = new ArrayList<>();

        List children = (List) root.get("childComponents");
        if (children != null) {
            // Traverse through children
            for (Object child : children) {
                Object type = ((Map) child).get("type");
                if (type != null && type.toString().equals("ROUTE")) {
                    Route route = new Route((Map) child, String.join("/", folderPath));
                    // Get all filters
                    List filters = (List) ((Map) child).get("childComponents");
                    for (Object filter : filters) {
                        route.getFilters().add(new Filter(route, (Map) filter));
                    }

                    routes.add(route);
                }
            }
        }

        List folders = (List) root.get("childFolders");
        if (folders != null) {
            // Traverse through children
            for (Object folder : folders) {
                List<String> path = new ArrayList<>(folderPath);
                path.add(((Map) folder).get("name").toString());

                routes.addAll(findAllRoutes((Map) folder, path));
            }
        }

        return routes;
    }

    protected void saveJUnitXml(AbstractBuild<?, ?> build, TestSuite suite) throws IOException, InterruptedException {
        FilePath rootDir = build.getWorkspace();

        for (TestComponent component : suite.getComponents()) {
            FilePath output = new FilePath(rootDir, "TEST-" + component.getComponentName().replaceAll("[^a-zA-Z0-9\\_]+", "") + ".xml");
            try (OutputStream os = output.write()) {
                int errors = 0;
                int skipped = 0;
                int failures = 0;

                StringBuilder sb = new StringBuilder(500);

                os.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes());
                
                
                for (TestCase c : component.getTests()) {
                    String clsName = component.getComponentName() + "." + (c.getConnectorName() == null ? c.getFilterName() : c.getConnectorName());
                    String name = c.getName();

                    // Write out the XML
                    sb.append("\t<testcase name=\"")
                            .append(name)
                            .append("\" classname=\"")
                            .append(clsName)
                            .append("\" time=\"0.0\">\n");

                    switch (c.getResult()) {
                        case "FAIL":
                            failures++;
                            sb.append("\t\t<failure type=\"Fail\" message=\"Rhapsody returned a fail status\" />\n");
                            break;
                        case "SKIPPED":
                            skipped++;
                            sb.append("\t\t<skipped message=\"Rhapsody returned a skip status\" />\n");
                            break;
                        case "ERROR":
                        case "INVALID":
                            errors++;
                            sb.append("\t\t<error type=\"Error\" message=\"Rhapsody returned an error status\" />\n");
                            break;
                    }

                    sb.append("\t</testcase>\n");

                }

                os.write(("<testsuite xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                        + "xsi:noNamespaceSchemaLocation=\"https://maven.apache.org/surefire/maven-surefire-plugin/xsd/surefire-test-report-3.0.xsd\" "
                        + "version=\"3.0\" "
                        + "name=\"" + component.getComponentName() + "\" "
                        + "group=\"" + component.getFolderPath() + "\" "
                        + "time=\""+ String.valueOf(((float) component.getDuration() / 1000)) +"\" "
                        + "tests=\"" + component.getTests().size() + "\" "
                        + "errors=\"" + errors + "\" skipped=\"" + skipped + "\" failures=\"" + failures + "\">\n").getBytes());

                os.write(sb.toString().getBytes());

                os.write("</testsuite>\n".getBytes());
            }
        }
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public FormValidation doCheckRoutePatterns(@QueryParameter String routePatterns) {
            if (routePatterns == null || routePatterns.trim().isEmpty()) {
                return FormValidation.error("Route patterns is required");
            }

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Rhapsody Test Executor";
        }

    }

}
