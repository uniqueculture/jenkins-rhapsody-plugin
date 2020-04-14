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
import org.ahn.rhapsody.ci.json.TestComponent;
import org.ahn.rhapsody.ci.model.Component;
import org.apache.http.client.HttpClient;
import org.slf4j.Logger;

/**
 * As there is a limit of 1 running test per instance of Rhapsody
 * For now this implementation insures only one instance of the executor will 
 * be shared across multiple builds regardless of Rhapsody instance
 * TODO: Support multiple executors for each Rhapsody instance
 *
 * @author me
 */
public interface RhapsodyTestExecutor {
    
    TestComponent executeTests(Component component, String baseUrl, HttpClient client, ObjectMapper mapper, Logger logger);
    
}
