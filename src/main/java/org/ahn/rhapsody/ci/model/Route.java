/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ahn.rhapsody.ci.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 * @author me
 */
public class Route extends Component {
    
    List<Filter> filters;

    public Route(Map data, String folder) {
        super(data, folder);
        
        this.filters = new ArrayList<>();
    }

    public List<Filter> getFilters() {
        return filters;
    }

    public void setFilters(List<Filter> filters) {
        this.filters = filters;
    }
    
    @Override
    public String toString() {
        return "Route{" + super.toString() + '}';
    }
}
