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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 *
 * @author me
 */
public class RhapsodyChangeEntry extends RhapsodyLogEntry {

    private String comment = "";
    private String version = "";
    private List<String> routes = new ArrayList<>();
    private List<String> commPoints = new ArrayList<>();
    private List<String> definitions = new ArrayList<>();

    public RhapsodyChangeEntry(Date date, String username, String message, String type) {
        super(date, username, message, type);

        parseMessage(message);
    }

    public RhapsodyChangeEntry(Date date, String version, String comment, String username, String message, String type) {
        super(date, username, message, type);

        this.version = version;
        this.comment = comment;

        parseMessage(message);
    }

    public String getComment() {
        return comment;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getRoutes() {
        return routes;
    }

    public List<String> getDefinitions() {
        return definitions;
    }

    public List<String> getCommPoints() {
        return commPoints;
    }

    private void parseMessage(String message) {
        String[] parts = message.split(RhapsodyLog.MESSAGE_SEPARATOR);
        String part, label, value;
        for (int i = 0; i < parts.length; i++) {
            part = parts[i];
            if (!part.contains(":")) {
                continue;
            }

            label = part.substring(0, part.indexOf(':')).trim();
            value = part.substring(part.indexOf(':') + 1).trim();

            if (label.contains("version")) {
                version = value;
            } else if (label.contains("Comment")) {
                comment = value;
            } else if (label.contains("Modified") && label.contains("routes")) {
                String[] routes = value.split(",");
                for (String route : routes) {
                    this.routes.add(route.trim());
                }
            } else if (label.contains("Modified") && label.contains("communication")) {
                String[] commPoints = value.split(",");
                for (String commPoint : commPoints) {
                    this.commPoints.add(commPoint.trim());
                }
            } else if (label.contains("Modified") && label.contains("definitions")) {
                String[] definitions = value.split(",");
                for (String definition : definitions) {
                    this.definitions.add(definition.trim());
                }
            }
        }
    }

}
