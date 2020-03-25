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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author me
 */
public class RhapsodyLog {

    private static final String AUDIT = "AUDIT";
    private static final String SYSTEM = "SYSTEM";

    private static final Logger LOGGER = LoggerFactory.getLogger(RhapsodyLog.class);
    public static final String MESSAGE_SEPARATOR = "\r";

    private String baseUrl;
    private HttpClient client;
    private ObjectMapper mapper;

    public RhapsodyLog(String baseUrl, HttpClient client, ObjectMapper mapper) {
        this.baseUrl = baseUrl;
        this.client = client;
        this.mapper = mapper;
    }

    public void setClient(HttpClient client) {
        if (client == null) {
            return;
        }

        this.client = client;
    }

    public void setMapper(ObjectMapper mapper) {
        if (mapper == null) {
            return;
        }

        this.mapper = mapper;
    }

    protected HttpResponse requestExport(String type, long startTime, long endTime) throws JsonProcessingException, IOException {
        LOGGER.info("Requesting audit logs from Rhapsody between {} and {}", startTime, endTime);

        // Get the changes via Audit log export
        HttpPost logsRequest = new HttpPost(baseUrl + "/api/logs/export");
        logsRequest.setHeader("Accept", "application/zip");
        logsRequest.setHeader("Content-Type", "application/json");

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);

        Date startDate = new Date(startTime);
        Date endDate = new Date(endTime);
        LOGGER.info("Start time: {}", df.format(startDate));
        LOGGER.info("End time: {}", df.format(endDate));

        Map logsEntity = new HashMap();
        Map timeRange = new HashMap();
        logsEntity.put("logType", type);
        logsEntity.put("timeRange", timeRange);
        timeRange.put("startTime", df.format(startDate));
        timeRange.put("endTime", df.format(endDate));
        // Set the POST payload
        logsRequest.setEntity(new ByteArrayEntity(mapper.writeValueAsBytes(logsEntity)));

        return client.execute(logsRequest);
    }

    protected synchronized boolean saveAudit(Path path, long startTime, long endTime) throws JsonProcessingException, IOException {
        HttpResponse response = null;
        try {
            response = requestExport(AUDIT, startTime, endTime);
            // Read the response and save to a file
            try (FileOutputStream os = new FileOutputStream(path.toFile())) {
                int bytes = IOUtils.copy(response.getEntity().getContent(), os);
                LOGGER.debug("Copied {} bytes", bytes);
            }
        } catch (Exception ex) {
            return false;
        } finally {
            HttpClientUtils.closeQuietly(response);
        }

        return true;
    }

    /**
     * EXPERIMENTAL
     * 
     * @param path
     * @param startTime
     * @param endTime
     * @return
     * @throws JsonProcessingException
     * @throws IOException 
     */
    protected synchronized boolean saveSystem(Path path, long startTime, long endTime) throws JsonProcessingException, IOException {
        HttpResponse response = null;
        try {
            response = requestExport(SYSTEM, startTime, endTime);
            // Read the response and save to a file
            try (FileOutputStream os = new FileOutputStream(path.toFile())) {
                int bytes = IOUtils.copy(response.getEntity().getContent(), os);
                LOGGER.debug("Copied {} bytes", bytes);
            }
        } catch (Exception ex) {
            return false;
        } finally {
            HttpClientUtils.closeQuietly(response);
        }

        return true;
    }

    public synchronized List<RhapsodyLogEntry> requestAuditEntries(long startTime, long endTime) throws IOException {
        LOGGER.info("Requesting audit logs from Rhapsody between {} and {}", startTime, endTime);
        List<RhapsodyLogEntry> entries = new ArrayList<>();

        HttpResponse logsResponse = requestExport(AUDIT, startTime, endTime);
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(logsResponse.getEntity().getContent())) {
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                LOGGER.trace("Log export zip entry: {}", entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }

                // Read the entrie
                StringBuilder sb = new StringBuilder(250);
                String prevLine = "";
                String line = "";
                int prevB = -1;
                int b;
                while ((b = zis.read()) != -1) {
                    // Assume \n
                    if (b == 10) {
                        // New line
                        line = sb.toString().trim();
                        if (prevLine.isEmpty()) {
                            prevLine = line;
                        } else {
                            if (line.matches("^\\d{4}\\-\\d{2}\\-\\d{2}.+")) {
                                // Parse the previous line
                                try {
                                    entries.add(parseAuditLine(prevLine));
                                } catch (ParseException e) {
                                    LOGGER.warn("Unable to parse line. Ignoring the line", e);
                                }

                                // Start a new log line
                                prevLine = line;
                            } else {
                                // Log line continues on another line
                                prevLine += MESSAGE_SEPARATOR + line.trim();
                            }
                        }

                        // Create new buffer
                        sb = new StringBuilder(250);
                    }

                    sb.append((char) b);

                    prevB = b;
                }

                // Parse the last line
                try {
                    entries.add(parseAuditLine(prevLine));
                } catch (ParseException e) {
                    LOGGER.warn("Unable to parse line. Ignoring the line", e);
                }
            }
        } finally {
            HttpClientUtils.closeQuietly(logsResponse);
        }

        return entries;
    }

    /**
     * EXPERIMENTAL
     * 
     * @param startTime
     * @param endTime
     * @return
     * @throws IOException 
     */
    protected synchronized List<RhapsodyLogEntry> requestSystemEntries(long startTime, long endTime) throws IOException {
        LOGGER.info("Requesting system logs from Rhapsody between {} and {}", startTime, endTime);
        List<RhapsodyLogEntry> entries = new ArrayList<>();

        HttpResponse logsResponse = requestExport(SYSTEM, startTime, endTime);
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(logsResponse.getEntity().getContent())) {
            ArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                LOGGER.trace("Log export zip entry: {}", entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }

                // Read the entrie
                StringBuilder sb = new StringBuilder(250);
                String prevLine = "";
                String line = "";
                int prevB = -1;
                int b;
                while ((b = zis.read()) != -1) {
                    // Assume \n
                    if (b == 10) {
                        // New line
                        line = sb.toString().trim();
                        if (prevLine.isEmpty()) {
                            prevLine = line;
                        } else {
                            if (line.matches("^\\d{4}\\-\\d{2}\\-\\d{2}.+")) {
                                // Parse the previous line
                                try {
                                    entries.add(parseSystemLine(prevLine));
                                } catch (ParseException e) {
                                    LOGGER.warn("Unable to parse line. Ignoring the line", e);
                                }

                                // Start a new log line
                                prevLine = line;
                            } else {
                                // Log line continues on another line
                                prevLine += MESSAGE_SEPARATOR + line.trim();
                            }
                        }

                        // Create new buffer
                        sb = new StringBuilder(250);
                    }

                    sb.append((char) b);

                    prevB = b;
                }

                // Parse the last line
                try {
                    entries.add(parseAuditLine(prevLine));
                } catch (ParseException e) {
                    LOGGER.warn("Unable to parse line. Ignoring the line", e);
                }
            }
        } finally {
            HttpClientUtils.closeQuietly(logsResponse);
        }

        // Change entries span multiple log lines, need to combine them
        Pattern userVersionPattern = Pattern.compile("by '([^\\(]+)' at version '([^']+)'", Pattern.CASE_INSENSITIVE);
        List<RhapsodyLogEntry> combinedEntries = new ArrayList<>();
        /*RhapsodyLogEntry entry, nextEntry = null;
        Iterator<RhapsodyLogEntry> iter = entries.iterator();
        while (iter.hasNext()) {
            entry = iter.next();
            if (iter.hasNext()) {
                nextEntry = iter.next();
            }

            if (entry.getMessage().contains("Configuration changes committed")
                    && nextEntry != null && nextEntry.getMessage().contains("changed by user")) {
                // Change entry start, contains commit comment and version
                String version = "";
                String username = "";
                String comment = "";

                Matcher userVersionMatcher = userVersionPattern.matcher(entry.getMessage());
                if (userVersionMatcher.matches()) {
                    username = userVersionMatcher.group(1);
                    version = userVersionMatcher.group(2);
                }

                // Comment
                String[] commentParts = entry.getMessage().split(MESSAGE_SEPARATOR);
                comment = commentParts[1].trim();
                // Add a change entry combined between current and next entries
                combinedEntries.add(new RhapsodyChangeEntry(entry.getDate(), version, comment, username, nextEntry.getMessage(), ""));
            } else {
                // Normal log entries, add both
                combinedEntries.add(entry);
                combinedEntries.add(nextEntry);
            }
        }*/

        return combinedEntries;
    }

    protected RhapsodyLogEntry parseAuditLine(String line) throws ParseException {
        String parts[] = line.split("\\|");
        if (parts.length < 8) {
            throw new ParseException(line, 0);
        }

        String datePart = parts[0].trim();
        String timePart = parts[1].trim();
        String username = parts[5].trim();
        String message = parts[6].trim();
        String type = parts[7].trim();

        // 2020-03-23 12:20:47.763-04:00
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSXXX");
        Date date = dateFormat.parse(datePart + " " + timePart);

        if (type.contains("Change")) {
            return new RhapsodyChangeEntry(date, username, message, type);
        } else {
            return new RhapsodyLogEntry(date, username, message, type);
        }
    }

    /**
     * EXPERIMENTAL
     * 
     * @param line
     * @return
     * @throws ParseException 
     */
    protected RhapsodyLogEntry parseSystemLine(String line) throws ParseException {
        String parts[] = line.split("\\|");
        if (parts.length < 7) {
            throw new ParseException(line, 0);
        }

        String datePart = parts[0].trim();
        String timePart = parts[1].trim();
        String logger = parts[4].trim();
        String component = parts[5].trim();
        String message = parts[6].trim();

        // 2020-03-23 12:20:47.763-04:00
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSXXX");
        Date date = dateFormat.parse(datePart + " " + timePart);

        return new RhapsodyLogEntry(date, logger, message, "");
    }
}
