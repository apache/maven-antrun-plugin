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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.apache.maven.plugin.logging.Log;
import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MavenLoggerTest {

    @Test
    void prefixesMessagesFromAntWorkerThreadsWhenEnabled() throws InterruptedException {
        List<String> messages = new ArrayList<>();
        MavenLogger logger = new MavenLogger(recordingLog(messages), true);
        logger.setMessageOutputLevel(Project.MSG_INFO);

        Thread worker = new Thread(() -> logger.messageLogged(messageEvent("worker output")), "ant-worker");
        worker.start();
        worker.join();

        assertEquals(1, messages.size());
        assertEquals("[" + Thread.currentThread().getName() + "] worker output", messages.get(0));
    }

    @Test
    void doesNotPrefixMessagesWithDefaultConstructor() throws InterruptedException {
        List<String> messages = new ArrayList<>();
        MavenLogger logger = new MavenLogger(recordingLog(messages));
        logger.setMessageOutputLevel(Project.MSG_INFO);

        Thread worker = new Thread(() -> logger.messageLogged(messageEvent("worker output")), "ant-worker");
        worker.start();
        worker.join();

        assertEquals(1, messages.size());
        assertEquals("worker output", messages.get(0));
    }

    private static BuildEvent messageEvent(String message) {
        BuildEvent event = new BuildEvent(new Project());
        event.setMessage(message, Project.MSG_INFO);
        return event;
    }

    private static Log recordingLog(List<String> messages) {
        return (Log) Proxy.newProxyInstance(
                Log.class.getClassLoader(), new Class<?>[] {Log.class}, (proxy, method, args) -> {
                    if ("info".equals(method.getName()) && args != null && args.length > 0) {
                        messages.add(args[0].toString());
                    }
                    return method.getReturnType() == boolean.class ? true : null;
                });
    }
}
