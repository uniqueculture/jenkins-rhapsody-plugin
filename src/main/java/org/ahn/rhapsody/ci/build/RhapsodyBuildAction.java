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

import com.fasterxml.jackson.databind.ObjectMapper;
import hudson.model.Run;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import jenkins.model.RunAction2;
import org.ahn.rhapsody.ci.json.TestSuite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author me
 */
public class RhapsodyBuildAction implements RunAction2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(RhapsodyBuildAction.class);

    private transient Run run;
    private transient TestSuite testSuite;

    private int successCount;
    private int failCount;
    private int totalCount;
    private int skippedCount;

    public RhapsodyBuildAction(int successCount, int failCount, int skippedCount, int totalCount) {
        this.successCount = successCount;
        this.failCount = failCount;
        this.totalCount = totalCount;
        this.skippedCount = skippedCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }
    
    public Run getRun() {
        return run;
    }

    public void setTestSuite(TestSuite testSuite) {
        this.testSuite = testSuite;
    }

    public TestSuite getTestSuite() {
        if (testSuite == null) {
            load();
        }
        
        return testSuite;
    }
    
    private void load() {
        File testSuiteFile = new File(run.getRootDir(), "rh-test-suite.json");
        if (!testSuiteFile.canRead()) {
            LOGGER.warn("Test suite file does not exists or is unreadable");
            return;
        }
        
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = new FileInputStream(testSuiteFile)) {
            testSuite = mapper.readValue(is, TestSuite.class);
        } catch (Exception ex) {
            LOGGER.error("Exception loading test suite file", ex);
        }
    }
    
    @Override
    public String getIconFileName() {
        return "clipboard.png";
    }

    @Override
    public String getDisplayName() {
        return "Rhapsody Test Result";
    }

    @Override
    public String getUrlName() {
        return "rh-test";
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

}
