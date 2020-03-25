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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import org.junit.Ignore;
import org.junit.Test;
import static org.mockito.Matchers.isA;
import org.mockito.Mockito;
import static org.mockito.Mockito.when;

/**
 *
 * @author me
 */
public class RhapsodyLogTest {

    @Test
    @Ignore
    public void testRequestSystemEntries() throws IOException, URISyntaxException {
        ClassLoader classLoader = getClass().getClassLoader();
        byte[] responseBytes = Files.readAllBytes(Path.of(classLoader.getResource("system_log.zip").toURI()));

        HttpClient client = mockHttpClient(responseBytes);
        ObjectMapper mapper = new ObjectMapper();

        RhapsodyLog log = new RhapsodyLog("https://localhost:8444", client, mapper);
        List<RhapsodyLogEntry> entries = log.requestSystemEntries(0, 0);
        List<RhapsodyChangeEntry> changes = entries.stream().filter(e -> e instanceof RhapsodyChangeEntry).map(e -> (RhapsodyChangeEntry) e).collect(Collectors.toList());

        assertNotNull(changes);
        assertFalse(changes.isEmpty());
    }

    protected HttpClient mockHttpClient(byte[] responseBytes) throws IOException {
        HttpClient client = Mockito.mock(HttpClient.class);
        HttpResponse response = Mockito.mock(HttpResponse.class);
        HttpEntity entity = Mockito.mock(HttpEntity.class);
        StatusLine statusLine = Mockito.mock(StatusLine.class);
        when(client.execute(isA(HttpUriRequest.class))).thenReturn(response);
        when(response.getEntity()).thenReturn(entity);
        when(response.getStatusLine()).thenReturn(statusLine);
        when(entity.getContent()).thenReturn(new ByteArrayInputStream(responseBytes));
        when(statusLine.getStatusCode()).thenReturn(200);
        when(statusLine.getReasonPhrase()).thenReturn("");

        return client;
    }
}
