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
public class Filter extends Component {

    Route route;

    public Filter(Map data) {
        super(data, "");
    }

    public Filter(Route route, Map data) {
        super(data, route.getFolder());
        this.route = route;
    }

    @Override
    public String toString() {
        return "Filter{" + super.toString() + ", route=" + route + '}';
    }

    public Route getRoute() {
        return route;
    }
    
    
}
