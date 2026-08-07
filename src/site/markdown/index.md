---
title: Introduction
author: 
  - Kenney Westerhof, Franz Allan Valencia See
date: 2013-07-22
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Apache Maven AntRun Plugin
This plugin runs Ant tasks from inside Maven. You can embed your Ant scripts in the POM.

This plugin does not intend to fill the POM with your Ant tasks. Move all your Ant tasks to a `build.xml` file. Call the file from the POM with Ant's [&lt;ant/&gt; task](https://ant.apache.org/manual/Tasks/ant.html).

The main purpose of this plugin is to help projects migrate from Ant to Maven. Some projects depend on custom build functions that Maven does not provide by default. These projects cannot migrate yet.

## Goals Overview

- [antrun:run](./run-mojo.html) runs Ant tasks for Maven.
## Major Version Upgrade to version 3.0.0

The following parameters are removed from the plugin configuration:

- `tasks`: use `target` instead.
- `sourceRoot` and `testSourceRoot`: use the [build-helper-maven-plugin](https://www.mojohaus.org/build-helper-maven-plugin/) instead. Use its [add-source](https://www.mojohaus.org/build-helper-maven-plugin/add-source-mojo.html) and [add-test-source](https://www.mojohaus.org/build-helper-maven-plugin/add-test-source-mojo.html) goals.

The format `maven.dependency.groupId.artifactId[.classifier].type.path` no longer references the path of a project dependency as an Ant property. Use `groupId:artifactId:type[:classifier]` instead.

## Usage

The [usage page](./usage.html) contains general instructions for the AntRun Plugin. The examples below describe specific use cases.

If you have questions about the plugin, read the [FAQ](./faq.html). Contact the [user mailing list](./mailing-lists.html). The mailing list posts are archived. An older thread can contain the answer to your question. Browse the [mail archive](./mailing-lists.html).

If the plugin misses a feature or has a defect, report it in the [issue tracker](./issue-management.html). Describe the issue in detail. The developers must reproduce your problem to fix a bug. Attach debug logs, POMs, or a small demo project to the issue. Patches are welcome. Get the project from the [source repository](./scm.html). Read the [guide to helping with Maven](/guides/development/guide-helping.html) for more information.

## Examples

These examples show how to use the Maven AntRun Plugin:

- [Referencing the Maven Classpaths](./examples/classpaths.html)
- [Using `<target/>` Attributes](./examples/tasksAttributes.html)
- [Using tasks not included in Ant's default jar](./examples/customTasks.html)
