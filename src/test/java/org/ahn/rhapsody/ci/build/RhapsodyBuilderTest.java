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
package org.ahn.rhapsody.ci.build;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.ahn.rhapsody.ci.model.Component;
import org.ahn.rhapsody.ci.model.Filter;
import org.ahn.rhapsody.ci.model.Route;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author me
 */
public class RhapsodyBuilderTest {

    @Test
    public void testFiltering() {
        RhapsodyBuilder builder = new RhapsodyBuilder("", "", true);
        List<Route> routes = new ArrayList<>();

        for (int i = 0; i < 101; i = i + 5) {
            Map<String, String> data = new HashMap<>();
            data.put("name", "Route " + i);

            Route route = new Route(data, "/");

            List<Filter> filters = new ArrayList<>();
            for (int y = 0; y < 5; y++) {
                Map<String, String> filterData = new HashMap<>();
                filterData.put("name", "Filter " + i + "-" + y);

                Filter filter = new Filter(route, filterData);
                filters.add(filter);
            }

            route.setFilters(filters);
            routes.add(route);
        }

        try {
            builder.filterComponentsToTest(routes, "", "");
            fail("Illegal argument exception is expected");
        } catch (IllegalArgumentException ex) {
            // Expected
        }

        List<Component> allComponents = builder.filterComponentsToTest(routes, "*", "");
        assertEquals(routes.size(), allComponents.size());

        List<Component> onlyOneRoute = builder.filterComponentsToTest(routes, "Route 10", "");
        assertEquals(1, onlyOneRoute.size());

        List<Component> onlyRoutesWithOne = builder.filterComponentsToTest(routes, "Route 1*", "");
        // Expect: Route 1, Route 10, Route 15
        assertEquals(3, onlyRoutesWithOne.size());

        List<Component> onlyFilters = builder.filterComponentsToTest(routes, "Route 10", "*");
        assertEquals(5, onlyFilters.size());
        for (Component c : onlyFilters) {
            assertTrue("Component is expected to be Filter", c instanceof Filter);
        }

        List<Component> filteredFilters = builder.filterComponentsToTest(routes, "Route 10", "Filter 10-1");
        assertEquals(1, filteredFilters.size());
        for (Component c : onlyFilters) {
            assertTrue("Component is expected to be Filter", c instanceof Filter);
        }
    }

}
