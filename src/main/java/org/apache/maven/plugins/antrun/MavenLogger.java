/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.antrun;

import java.io.PrintStream;

import org.apache.maven.plugin.logging.Log;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;

/**
 * Redirects build events from {@link DefaultLogger} to {@link Log}.
 */
public class MavenLogger extends DefaultLogger {

    private final Log log;
    private final Thread ownerThread;
    private final String ownerThreadName;
    private final boolean includeOwnerThreadName;

    /**
     * Creates a logger that forwards Ant messages to Maven without adding an owner-thread prefix.
     *
     * @param log the Maven logger
     */
    public MavenLogger(Log log) {
        this(log, false);
    }

    /**
     * Creates a logger that forwards Ant messages to Maven.
     *
     * @param log the Maven logger
     * @param includeOwnerThreadName whether to prefix messages emitted by Ant worker threads with the owning Maven
     *                              thread name
     * @since 3.3.0
     */
    public MavenLogger(Log log, boolean includeOwnerThreadName) {
        this.log = log;
        this.ownerThread = Thread.currentThread();
        this.ownerThreadName = ownerThread.getName();
        this.includeOwnerThreadName = includeOwnerThreadName;
    }

    @Override
    public void messageLogged(BuildEvent event) {
        String message = event.getMessage();
        boolean prefixMessage = includeOwnerThreadName && message != null && Thread.currentThread() != ownerThread;
        if (prefixMessage) {
            event.setMessage("[" + ownerThreadName + "] " + message, event.getPriority());
        }
        try {
            super.messageLogged(event);
        } finally {
            if (prefixMessage) {
                event.setMessage(message, event.getPriority());
            }
        }
    }

    @Override
    protected void printMessage(final String message, final PrintStream stream, final int priority) {
        switch (priority) {
            case Project.MSG_ERR:
                log.error(message);
                break;
            case Project.MSG_WARN:
                log.warn(message);
                break;
            case Project.MSG_DEBUG:
            case Project.MSG_VERBOSE:
                log.debug(message);
                break;
            case Project.MSG_INFO:
            default:
                log.info(message);
                break;
        }
    }
}
