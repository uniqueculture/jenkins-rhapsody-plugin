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
package org.ahn.rhapsody.ci.model;

import java.util.Map;

/**
 *
 * @author me
 */
public class Component {

    String uuid;
    String id;
    String name;
    String type;
    String folder;

    public Component(Map data, String folder) {
        if (data.containsKey("uuid")) {
            uuid = data.get("uuid").toString();
        }

        if (data.containsKey("id")) {
            id = data.get("id").toString();
        }

        if (data.containsKey("name")) {
            name = data.get("name").toString();
        }

        if (data.containsKey("type")) {
            type = data.get("type").toString();
        }

        this.folder = folder;
    }

    @Override
    public String toString() {
        return "Component{" + "uuid=" + uuid + ", id=" + id + ", name=" + name + ", type=" + type + ", folder=" + folder + "}";
    }

    public String getUuid() {
        return uuid;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getFolder() {
        return folder;
    }
}
