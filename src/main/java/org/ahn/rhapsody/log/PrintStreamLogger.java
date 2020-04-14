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
package org.ahn.rhapsody.log;

import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.MessageFormatter;

/**
 *
 * @author me
 */
public class PrintStreamLogger extends AbstractLogger {

    private boolean showDateTime = true;
    private boolean showThreadName = false;
    private boolean showShortLogName = true;
    private boolean showLogName = false;
    private final PrintStream targetStream;
    private final DateFormat dateFormat = DateFormat.getTimeInstance();

    public PrintStreamLogger(PrintStream targetStream) {
        this.targetStream = targetStream;
    }

    @Override
    protected String getFullyQualifiedCallerName() {
        return "";
    }

    /**
     * This is our internal implementation for logging regular
     * (non-parameterized) log messages.
     *
     * @param level One of the LOG_LEVEL_XXX constants defining the log level
     * @param message The message itself
     * @param t The exception whose stack trace should be logged
     */
    @Override
    protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern, Object[] arguments,
            Throwable t) {

        List<Marker> markers = null;

        if (marker != null) {
            markers = new ArrayList<>();
            markers.add(marker);
        }

        innerHandleNormalizedLoggingCall(level, markers, messagePattern, arguments, t);
    }

    private void innerHandleNormalizedLoggingCall(Level level, List<Marker> markers, String messagePattern, Object[] arguments,
            Throwable t) {

        StringBuilder buf = new StringBuilder(32);

        // Append date-time if so configured
        if (showDateTime) {
            buf.append(dateFormat.format(new Date()));
            buf.append(' ');
        }

        // Append current thread name if so configured
        if (showThreadName) {
            buf.append('[');
            buf.append(Thread.currentThread().getName());
            buf.append("] ");
        }

        // Append a readable representation of the log level
        String levelStr = level.name();
        buf.append(levelStr);
        buf.append(' ');

        // Append the name of the log instance if so configured
        //buf.append(String.valueOf(name)).append(" - ");
        buf.append(" - ");

        String formattedMessage = MessageFormatter.arrayFormat(messagePattern, arguments).getMessage();

        // Append the message
        buf.append(formattedMessage);

        write(buf, t);
    }

    void write(StringBuilder buf, Throwable t) {
        targetStream.println(buf.toString());
        writeThrowable(t, targetStream);
        targetStream.flush();
    }

    protected void writeThrowable(Throwable t, PrintStream targetStream) {
        if (t != null) {
            t.printStackTrace(targetStream);
        }
    }

    @Override
    public boolean isTraceEnabled() {
        return true;
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
        return true;
    }

    @Override
    public boolean isDebugEnabled() {
        return true;
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
        return true;
    }

    @Override
    public boolean isInfoEnabled() {
        return true;
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
        return true;
    }

    @Override
    public boolean isWarnEnabled() {
        return true;
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
        return true;
    }

    @Override
    public boolean isErrorEnabled() {
        return true;
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
        return true;
    }

}
