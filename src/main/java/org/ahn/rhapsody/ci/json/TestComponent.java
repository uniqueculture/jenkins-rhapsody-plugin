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
package org.ahn.rhapsody.ci.json;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author me
 */
public class TestComponent {
    
    String componentName;
    String componentId;
    String folderPath;
    String error;
    
    int totalCount = 0;
    int passedCount = 0;
    int executedCount = 0;
    int failedCount = 0;
    int errorCount = 0;
    int skippedCount = 0;
    
    List<TestCase> tests;

    public TestComponent() {
    }
    
    public TestComponent(String componentId, String componentName, String folderPath) {
        this.componentId = componentId;
        this.componentName = componentName;
        this.folderPath = folderPath;
        this.tests = new ArrayList<>();
    }
    
    public String getComponentName() {
        return componentName;
    }

    public String getComponentId() {
        return componentId;
    }

    public String getFolderPath() {
        return folderPath;
    }    

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
    
    public TestComponent addTest(TestCase tCase) {
        tests.add(tCase);
        return this;
    }

    public List<TestCase> getTests() {
        return tests;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getPassedCount() {
        return passedCount;
    }

    public void setPassedCount(int passedCount) {
        this.passedCount = passedCount;
    }

    public int getExecutedCount() {
        return executedCount;
    }

    public void setExecutedCount(int executedCount) {
        this.executedCount = executedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }
    
    
    
}
