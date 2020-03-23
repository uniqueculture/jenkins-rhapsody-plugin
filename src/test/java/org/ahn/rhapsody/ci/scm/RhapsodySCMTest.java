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

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;
import static org.mockito.Matchers.*;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

/**
 *
 * @author me
 */
public class RhapsodySCMTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testCheckout() throws IOException, InterruptedException, ExecutionException {
        HttpClient client = Mockito.mock(HttpClient.class);
        HttpResponse response = Mockito.mock(HttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);
        StatusLine statusLine = Mockito.mock(StatusLine.class);
        when(client.execute(isA(HttpUriRequest.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(entity);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream("Testing".getBytes()));
        when(statusLine.getStatusCode()).thenReturn(200);
        when(statusLine.getReasonPhrase()).thenReturn("");

        FreeStyleProject project = jenkins.createFreeStyleProject();
        RhapsodySCM scm = new RhapsodySCM("", "");
        scm.setHttpClient(client);

        project.setScm(scm);
        FreeStyleBuild build = project.scheduleBuild2(0).get();
        // Make sure the components were checked out
        FilePath componentsFile = new FilePath(build.getWorkspace(), RhapsodySCM.COMPONENTS_FILENAME);
        assertTrue(componentsFile.exists());

        // Check for the checkout action
        RhapsodySCMAction a = build.getAction(RhapsodySCMAction.class);
        assertNotNull(a);
    }

}
