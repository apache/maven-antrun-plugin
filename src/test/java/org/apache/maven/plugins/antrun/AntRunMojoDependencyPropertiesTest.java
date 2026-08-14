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

import java.io.File;
import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A dependency whose artifact was never resolved has a null file. Registering the
 * per-dependency path properties must not fall over on it.
 */
class AntRunMojoDependencyPropertiesTest {

    /**
     * execute() defaults propertyPrefix to "" before any of this runs; a directly
     * constructed mojo has not been through that, so mirror it here.
     */
    private static AntRunMojo mojoWithEmptyPrefix() throws Exception {
        AntRunMojo mojo = new AntRunMojo(null);
        Field prefix = AntRunMojo.class.getDeclaredField("propertyPrefix");
        prefix.setAccessible(true);
        prefix.set(mojo, "");
        return mojo;
    }

    private static Artifact artifact(String artifactId, File file) {
        Artifact artifact = new DefaultArtifact(
                "org.example",
                artifactId,
                VersionRange.createFromVersion("1.0"),
                "compile",
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(file);
        return artifact;
    }

    @Test
    void unresolvedDependencyIsSkippedInsteadOfThrowing() throws Exception {
        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("unresolved", null));

        Project antProject = new Project();
        AntRunMojo mojo = mojoWithEmptyPrefix();

        assertDoesNotThrow(() -> mojo.setDependencyFileProperties(artifacts, antProject));
        assertNull(
                antProject.getProperty("org.example:unresolved:jar"),
                "no path property should be set for a dependency with no artifact file");
    }

    @Test
    void resolvedDependenciesStillGetTheirPathProperty() throws Exception {
        File resolved = new File("target", "resolved-1.0.jar");

        Set<Artifact> artifacts = new LinkedHashSet<>();
        artifacts.add(artifact("unresolved", null));
        artifacts.add(artifact("resolved", resolved));

        Project antProject = new Project();
        AntRunMojo mojo = mojoWithEmptyPrefix();

        mojo.setDependencyFileProperties(artifacts, antProject);

        // The unresolved artifact is listed first, so this also proves one bad
        // dependency no longer prevents the rest from being registered.
        assertEquals(
                resolved.getPath(),
                antProject.getProperty("org.example:resolved:jar"),
                "resolved dependencies must still get their path property");
    }

    @Test
    void nullArtifactSetIsTolerated() throws Exception {
        Project antProject = new Project();
        AntRunMojo mojo = mojoWithEmptyPrefix();

        assertDoesNotThrow(() -> mojo.setDependencyFileProperties(null, antProject));
    }
}
