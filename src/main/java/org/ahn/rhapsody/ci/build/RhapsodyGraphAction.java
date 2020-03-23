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

import hudson.Functions;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Run;
import hudson.util.Area;
import hudson.util.ChartUtil;
import hudson.util.ColorPalette;
import hudson.util.DataSetBuilder;
import hudson.util.RunList;
import hudson.util.ShiftedCategoryAxis;
import hudson.util.StackedAreaRenderer2;
import java.awt.Color;
import java.io.IOException;
import java.util.Iterator;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StackedAreaRenderer;
import org.jfree.data.category.CategoryDataset;
import org.jfree.ui.RectangleInsets;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

/**
 *
 * @author me
 */
public class RhapsodyGraphAction implements Action {

    private transient final AbstractProject<?, ?> project;

    public RhapsodyGraphAction(AbstractProject<?, ?> project) {
        this.project = project;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "rh-graph";
    }

    /**
     * Generates a PNG image for the test result trend.
     */
    public void doTrend(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (ChartUtil.awtProblemCause != null) {
            // not available. send out error message
            rsp.sendRedirect2(req.getContextPath() + "/images/headless.png");
            return;
        }

        if (req.checkIfModified(project.getLastCompletedBuild().getTimestamp(), rsp)) {
            return;
        }

        ChartUtil.generateGraph(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
    }

    /**
     * Generates a clickable map HTML for
     * {@link #doGraph(StaplerRequest, StaplerResponse)}.
     */
    public void doMap(StaplerRequest req, StaplerResponse rsp) throws IOException {
        if (req.checkIfModified(project.getLastCompletedBuild().getTimestamp(), rsp)) {
            return;
        }
        ChartUtil.generateClickableMap(req, rsp, createChart(req, buildDataSet(req)), calcDefaultSize());
    }

    private CategoryDataset buildDataSet(StaplerRequest req) {
        boolean failureOnly = Boolean.valueOf(req.getParameter("failureOnly"));

        DataSetBuilder<String, ChartUtil.NumberOnlyBuildLabel> dsb = new DataSetBuilder<>();

        RunList builds = project.getBuilds();
        Iterator<Run> iter = builds.iterator();
        while (iter.hasNext()) {
            Run run = iter.next();
            RhapsodyBuildAction action = run.getAction(RhapsodyBuildAction.class);
            if (action != null) {
                dsb.add(action.getFailCount(), "failed", new ChartUtil.NumberOnlyBuildLabel(run));
                dsb.add(action.getSuccessCount(), "success", new ChartUtil.NumberOnlyBuildLabel(run));
                dsb.add(action.getTotalCount(), "total", new ChartUtil.NumberOnlyBuildLabel(run));
            }
        }

        /*int cap = Integer.getInteger(AbstractTestResultAction.class.getName() + ".test.trend.max", Integer.MAX_VALUE);
        int count = 0;
        for (AbstractTestResultAction<?> a = this; a != null; a = a.getPreviousResult(AbstractTestResultAction.class, false)) {
            if (++count > cap) {
                LOGGER.log(Level.FINE, "capping test trend for {0} at {1}", new Object[]{run, cap});
                break;
            }
            dsb.add(a.getFailCount(), "failed", new ChartUtil.NumberOnlyBuildLabel(a.run));
            if (!failureOnly) {
                dsb.add(a.getSkipCount(), "skipped", new ChartUtil.NumberOnlyBuildLabel(a.run));
                dsb.add(a.getTotalCount() - a.getFailCount() - a.getSkipCount(), "total", new ChartUtil.NumberOnlyBuildLabel(a.run));
            }
        }
        LOGGER.log(Level.FINER, "total test trend count for {0}: {1}", new Object[]{run, count});*/
        return dsb.build();
    }

    /**
     * Determines the default size of the trend graph.
     *
     * This is default because the query parameter can choose arbitrary size. If
     * the screen resolution is too low, use a smaller size.
     */
    private Area calcDefaultSize() {
        Area res = Functions.getScreenResolution();
        if (res != null && res.width <= 800) {
            return new Area(250, 100);
        } else {
            return new Area(500, 200);
        }
    }

    private JFreeChart createChart(StaplerRequest req, CategoryDataset dataset) {

        final String relPath = getRelPath(req);

        final JFreeChart chart = ChartFactory.createStackedAreaChart(
                null, // chart title
                null, // unused
                "count", // range axis label
                dataset, // data
                PlotOrientation.VERTICAL, // orientation
                false, // include legend
                true, // tooltips
                false // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...
        // set the background color for the chart...
//        final StandardLegend legend = (StandardLegend) chart.getLegend();
//        legend.setAnchor(StandardLegend.SOUTH);
        chart.setBackgroundPaint(Color.white);

        final CategoryPlot plot = chart.getCategoryPlot();

        // plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setBackgroundPaint(Color.WHITE);
        plot.setOutlinePaint(null);
        plot.setForegroundAlpha(0.8f);
//        plot.setDomainGridlinesVisible(true);
//        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinesVisible(true);
        plot.setRangeGridlinePaint(Color.black);

        CategoryAxis domainAxis = new ShiftedCategoryAxis(null);
        plot.setDomainAxis(domainAxis);
        domainAxis.setCategoryLabelPositions(CategoryLabelPositions.UP_90);
        domainAxis.setLowerMargin(0.0);
        domainAxis.setUpperMargin(0.0);
        domainAxis.setCategoryMargin(0.0);

        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());

        StackedAreaRenderer ar = new StackedAreaRenderer2() {
            @Override
            public String generateURL(CategoryDataset dataset, int row, int column) {
                ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                return relPath + label.getRun().getNumber() + "/rh-test/";
            }

            @Override
            public String generateToolTip(CategoryDataset dataset, int row, int column) {
                ChartUtil.NumberOnlyBuildLabel label = (ChartUtil.NumberOnlyBuildLabel) dataset.getColumnKey(column);
                RhapsodyBuildAction a = label.getRun().getAction(RhapsodyBuildAction.class);
                switch (row) {
                    case 0:
                        return label.getRun().getDisplayName() + ": " + a.getFailCount() + " failures";
                    case 1:
                        return label.getRun().getDisplayName() + ": " + a.getSuccessCount()+ " success";
                    default:
                        return label.getRun().getDisplayName() + ": " + a.getTotalCount() + " total";
                }
            }
        };
        plot.setRenderer(ar);
        ar.setSeriesPaint(0, ColorPalette.RED); // Failures.
        ar.setSeriesPaint(1, ColorPalette.YELLOW); // Skips.
        ar.setSeriesPaint(2, ColorPalette.BLUE); // Total.

        // crop extra space around the graph
        plot.setInsets(new RectangleInsets(0, 0, 0, 5.0));

        return chart;
    }

    private String getRelPath(StaplerRequest req) {
        String relPath = req.getParameter("rel");
        if (relPath == null) {
            return "";
        }
        return relPath;
    }

}
