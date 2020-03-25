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
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.RepositoryBrowser;
import hudson.util.Digester2;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.xml.sax.SAXException;

/**
 *
 * @author me
 */
public class RhapsodyChangeLogParser extends ChangeLogParser {

    @Override
    public ChangeLogSet<? extends ChangeLogSet.Entry> parse(Run build, RepositoryBrowser<?> browser, File changelogFile) throws IOException, SAXException {
        List<RhapsodyChangeLogSet.Entry> entries = new ArrayList<>();

        Digester2 digester = new Digester2();
        digester.push(entries);

        digester.addObjectCreate("*/changeset", RhapsodyChangeLogSet.Entry.class);
        // Read attributes and use setters to set the values
        digester.addSetProperties("*/changeset");
        // Read the children and use setters to set the values
        digester.addBeanPropertySetter("*/changeset/comment");
        digester.addBeanPropertySetter("*/changeset/user");
        // Read the date and use custom setter to parse the date
        digester.addBeanPropertySetter("*/changeset/date", "dateStr");

        // The digested node/change set is added to the list through {{List.add()}}
        digester.addSetNext("*/changeset", "add");

        // When digester reads a {{<items>}} child node of {{<changeset}} it will create a {{TeamFoundationChangeSet.Item}} object
        digester.addObjectCreate("*/changeset/items/item", RhapsodyChangeLogSet.Item.class);
        digester.addSetProperties("*/changeset/items/item");
        digester.addBeanPropertySetter("*/changeset/items/item", "component");
        // The digested node/item is added to the change set through {{TeamFoundationChangeSet.add()}}
        digester.addSetNext("*/changeset/items/item", "addItem");

        // Parse the file
        try (FileReader reader = new FileReader(changelogFile)) {
            digester.parse(reader);
        }
        
        // Reverse changes
        Collections.reverse(entries);

        return new RhapsodyChangeLogSet(build, entries);
    }

}
