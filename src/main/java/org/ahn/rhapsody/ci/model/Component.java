/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
