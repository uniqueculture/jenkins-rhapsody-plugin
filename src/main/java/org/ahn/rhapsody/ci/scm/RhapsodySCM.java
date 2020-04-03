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
package org.ahn.rhapsody.ci.scm;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.ahn.rhapsody.RhapsodyChangeEntry;
import org.ahn.rhapsody.RhapsodyLog;
import org.ahn.rhapsody.RhapsodyLogEntry;
import org.ahn.rhapsody.ci.RhapsodyRestHelper;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author me
 */
public class RhapsodySCM extends SCM {

    private static final Logger LOGGER = LoggerFactory.getLogger(RhapsodySCM.class);

    public static final String COMPONENTS_FILENAME = "rhapsody-components.json";

    private final String restUrl;
    private final String credentialsId;

    private transient HttpClient httpClient;

    @DataBoundConstructor
    public RhapsodySCM(String restUrl, String credentialsId) {
        this.restUrl = restUrl;
        this.credentialsId = credentialsId;
    }

    public String getRestUrl() {
        return restUrl;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    protected HttpClient getHttpClient(Run<?, ?> build) throws IOException {
        if (httpClient == null) {
            StandardUsernamePasswordCredentials credentials = CredentialsProvider.findCredentialById(credentialsId, StandardUsernamePasswordCredentials.class, build, Collections.EMPTY_LIST);
            if (credentials == null) {
                throw new AbortException("Rhapsody service credentials are not available");
            }

            CredentialsProvider.track(build, credentials);

            String pwd = credentials.getPassword().getPlainText();
            if (pwd == null) {
                throw new AbortException("Password is required");
            }

            httpClient = RhapsodyRestHelper.getHttpClient(credentials.getUsername(), pwd);
        }

        return httpClient;
    }

    protected void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    protected void exportAuditLogs(File changelogFile, HttpClient client, SCMRevisionState baseline) throws JsonProcessingException, IOException, InterruptedException {
        LOGGER.info("Exporting audit logs as change log for {}", restUrl);

        // Get the changes via Audit log export
        ObjectMapper mapper = new ObjectMapper();
        RhapsodyLog log = new RhapsodyLog(restUrl, client, mapper);

        long startTime = 0;
        long endTime = System.currentTimeMillis();
        if (baseline == null) {
            // Pull for the last 7 days
            startTime = System.currentTimeMillis() - Duration.ofDays(7).toMillis();
        } else {
            // Pull logs from last build
            startTime = ((RhapsodyAuditLogRevisionState) baseline).getTimestamp();
        }

        List<RhapsodyLogEntry> entries;
        try {
            entries = log.requestAuditEntries(startTime, endTime);
        } catch (Exception ex) {
            LOGGER.warn("Exception exporting audit logs", ex);
            entries = new ArrayList<>();
        }

        LOGGER.info("Total audit logs: {}", entries.size());
        // Filter to changes only
        List<RhapsodyChangeEntry> changes = entries.stream()
                .filter(e -> e instanceof RhapsodyChangeEntry)
                .map(e -> (RhapsodyChangeEntry) e)
                .filter(c -> !c.getVersion().isEmpty())
                .collect(Collectors.toList());
        LOGGER.info("Identified {} changes", changes.size());
        // Write out the changes to the changelog file
        try (PrintWriter writer = new PrintWriter(new FileWriter(changelogFile))) {
            writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            writer.println("<changelog>");
            for (RhapsodyChangeEntry change : changes) {
                writer.println(String.format("\t<changeset version=\"%s\">", change.getVersion()));
                writer.println(String.format("\t\t<date>%s</date>", Util.XS_DATETIME_FORMATTER.format(change.getDate())));
                writer.println(String.format("\t\t<user>%s</user>", change.getUsername()));
                writer.println(String.format("\t\t<comment>%s</comment>", StringEscapeUtils.escapeXml(change.getComment())));
                writer.println("\t\t<items>");
                change.getCommPoints().forEach((item) -> {
                    writer.println(String.format("\t\t\t<item action=\"%s\" type=\"%s\">%s</item>", "edit", "communication-point", item));
                });
                change.getDefinitions().forEach((item) -> {
                    writer.println(String.format("\t\t\t<item action=\"%s\" type=\"%s\">%s</item>", "edit", "definition", item));
                });
                change.getRoutes().forEach((item) -> {
                    writer.println(String.format("\t\t\t<item action=\"%s\" type=\"%s\">%s</item>", "edit", "route", item));
                });
                writer.println("\t\t</items>");
                writer.println("\t</changeset>");
            }
            writer.println("</changelog>");
        }

        LOGGER.info("Done exporting audit logs as change log for {}", restUrl);
    }

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        LOGGER.info("Checking out Rhapsody components");

        HttpClient client = getHttpClient(build);
        HttpGet componentsRequest = new HttpGet(restUrl + "/api/components");
        componentsRequest.addHeader("Accept", "application/json");

        HttpResponse componentsResponse = client.execute(componentsRequest);
        LOGGER.info("Received {} response from Rhapsody", componentsResponse.getStatusLine().getStatusCode());
        if (componentsResponse.getStatusLine().getStatusCode() != 200) {
            EntityUtils.consume(componentsResponse.getEntity());
            throw new AbortException(componentsResponse.getStatusLine().getStatusCode() + " "
                    + componentsResponse.getStatusLine().getReasonPhrase());
        }

        // Save the response in the workspace
        FilePath componentsFile = new FilePath(workspace, COMPONENTS_FILENAME);
        try (OutputStream os = componentsFile.write()) {
            IOUtils.copy(componentsResponse.getEntity().getContent(), os);
            LOGGER.info("Saved Rhapsody components from {}", restUrl);
        } finally {
            EntityUtils.consumeQuietly(componentsResponse.getEntity());
        }

        // Add action for the build step
        build.addAction(new RhapsodySCMAction(restUrl, credentialsId));

        if (baseline != null) {
            LOGGER.info("Baseline revision state datetime: {}", new Date(((RhapsodyAuditLogRevisionState) baseline).getTimestamp()));
        } else {
            LOGGER.info("Baseline revision state is null");
        }

        // Use audit logs as the change log
        exportAuditLogs(changelogFile, client, baseline);

        LOGGER.info("Checkout complete for {}", restUrl);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        LOGGER.info("Comparing remote revision with ");
        // Since no actual checkout occurs, force components refresh every time
        return new PollingResult(PollingResult.Change.SIGNIFICANT);
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        // Just timestamp the current build
        return new RhapsodyAuditLogRevisionState();
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return new RhapsodyChangeLogParser();
    }

    @Extension
    public static class DescriptorImpl extends SCMDescriptor {

        public DescriptorImpl() {
            super(RhapsodySCM.class);
        }

        @Override
        public String getDisplayName() {
            return "Rhapsody Server";
        }

        public FormValidation doCheckRestUrl(@QueryParameter String restUrl) {
            if (restUrl == null || restUrl.trim().isEmpty()) {
                return FormValidation.error("URL is required");
            }

            if (!restUrl.startsWith("http")) {
                return FormValidation.error("URL must start from HTTP protocol: http:// or https://");
            }

            return FormValidation.ok();
        }

        public ListBoxModel doFillCredentialsIdItems(
                @AncestorInPath Item item,
                @QueryParameter String credentialsId
        ) {
            LOGGER.info("doFillCredentialsIdItems");

            StandardListBoxModel result = new StandardListBoxModel();
            if (item == null && !Jenkins.get().hasPermission(Jenkins.ADMINISTER)
                    || item != null && !item.hasPermission(Item.EXTENDED_READ)) {
                return result;
            }

            LOGGER.info("Returning all credentials");
            return result
                    .includeAs(ACL.SYSTEM, item, StandardCredentials.class)
                    .includeCurrentValue(credentialsId);
        }
    }
}
