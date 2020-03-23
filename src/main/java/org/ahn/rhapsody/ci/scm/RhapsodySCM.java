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
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
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
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import jenkins.model.Jenkins;
import org.ahn.rhapsody.ci.RhapsodyRestHelper;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
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

    @Override
    public void checkout(Run<?, ?> build, Launcher launcher, FilePath workspace, TaskListener listener, File changelogFile, SCMRevisionState baseline) throws IOException, InterruptedException {
        LOGGER.info("Checking out Rhapsody components");

        HttpClient client = getHttpClient(build);
        HttpGet componentsRequest = new HttpGet(restUrl + "/api/components");
        componentsRequest.addHeader("Accept", "application/json");

        HttpResponse componentsResponse = client.execute(componentsRequest);
        LOGGER.info("Received {} response from Rhapsody", componentsResponse.getStatusLine().getStatusCode());
        if (componentsResponse.getStatusLine().getStatusCode() != 200) {
            throw new AbortException(componentsResponse.getStatusLine().getStatusCode() + " "
                    + componentsResponse.getStatusLine().getReasonPhrase());
        }

        // Save the response in the workspace
        FilePath componentsFile = new FilePath(workspace, COMPONENTS_FILENAME);
        try (OutputStream os = componentsFile.write()) {
            IOUtils.copy(componentsResponse.getEntity().getContent(), os);
        }

        // Add action for the build step
        build.addAction(new RhapsodySCMAction(restUrl, credentialsId));

        LOGGER.info("Saved Rhapsody components from {}", restUrl);
    }

    @Override
    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState baseline) throws IOException, InterruptedException {
        LOGGER.info("Comparing remote revision with ");

        return new PollingResult(PollingResult.Change.SIGNIFICANT);
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener) throws IOException, InterruptedException {
        return null;
    }

    @Override
    public ChangeLogParser createChangeLogParser() {
        return null;
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
