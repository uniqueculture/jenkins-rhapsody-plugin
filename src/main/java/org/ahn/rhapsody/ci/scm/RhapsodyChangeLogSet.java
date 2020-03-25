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

import hudson.model.Run;
import hudson.model.User;
import hudson.scm.ChangeLogSet;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author me
 */
public class RhapsodyChangeLogSet extends ChangeLogSet<RhapsodyChangeLogSet.Entry> {

    private List<Entry> entries;

    public RhapsodyChangeLogSet(Run<?, ?> run, List<Entry> entries) {
        super(run, null);

        this.entries = entries;
    }

    @Override
    public boolean isEmptySet() {
        return entries == null || entries.isEmpty();
    }

    @Override
    public Iterator<Entry> iterator() {
        return entries.iterator();
    }

    public static class Entry extends ChangeLogSet.Entry {

        private String version;
        private String comment;
        private String user;
        private List<Item> items = new ArrayList<>();
        private long timestamp;

        public void setComment(String comment) {
            this.comment = comment;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public void addItem(Item item) {
            items.add(item);
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public void setDateStr(String dateStr) {
            TemporalAccessor ta = DateTimeFormatter.ISO_INSTANT.parse(dateStr);
            Instant i = Instant.from(ta);
            timestamp = i.toEpochMilli();
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String getCommitId() {
            return version;
        }

        @Override
        public String getMsg() {
            return comment;
        }

        @Override
        public User getAuthor() {
            return User.getById(user, true);
        }

        @Override
        public Collection<String> getAffectedPaths() {
            return items.stream().map(i -> i.getComponent()).collect(Collectors.toList());
        }

        public List<Item> getItems() {
            return items;
        }
    }

    public static class Item {

        private String action;
        private String type;
        private String component;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getComponent() {
            return component;
        }

        public void setComponent(String component) {
            this.component = component;
        }

    }

}
