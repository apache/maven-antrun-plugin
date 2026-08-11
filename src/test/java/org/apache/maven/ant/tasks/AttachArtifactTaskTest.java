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
package org.apache.maven.ant.tasks;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugins.antrun.AntRunMojo;
import org.apache.maven.plugins.antrun.MavenAntRunProject;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link AttachArtifactTask}.
 */
class AttachArtifactTaskTest {

    private static final String CLASSIFIER = "tests";

    private static final String TYPE = "jar";

    @TempDir
    Path folder;

    private MavenProject attachedToProject;

    private String attachedClassifier;

    private String attachedType;

    private File attachedFile;

    /**
     * The task must also accept a plain {@link MavenProject} reference, such as the {@code maven.project} reference
     * registered by {@link AntRunMojo}, instead of throwing a {@link ClassCastException}.
     *
     * @throws IOException In case of problems
     */
    @Test
    void executesWhenProjectReferenceIsMavenProject() throws IOException {
        MavenProject mavenProject = new MavenProject();
        AttachArtifactTask task = newTask(AntRunMojo.DEFAULT_MAVEN_PROJECT_REFID, mavenProject);

        task.execute();

        assertEquals(mavenProject, attachedToProject);
        assertEquals(TYPE, attachedType);
        assertEquals(CLASSIFIER, attachedClassifier);
        assertEquals(task.getFile(), attachedFile);
    }

    /**
     * The default {@code maven.project.ref} reference, which wraps the project in a {@link MavenAntRunProject}, keeps
     * working.
     *
     * @throws IOException In case of problems
     */
    @Test
    void executesWhenProjectReferenceIsMavenAntRunProject() throws IOException {
        MavenProject mavenProject = new MavenProject();
        AttachArtifactTask task =
                newTask(AntRunMojo.DEFAULT_MAVEN_PROJECT_REF_REFID, new MavenAntRunProject(mavenProject));

        task.execute();

        assertEquals(mavenProject, attachedToProject);
    }

    /**
     * An incompatible reference type must be rejected with a clear {@link BuildException} rather than a raw
     * {@link ClassCastException}.
     *
     * @throws IOException In case of problems
     */
    @Test
    void throwsBuildExceptionWhenProjectReferenceHasIncompatibleType() throws IOException {
        AttachArtifactTask task = newTask(AntRunMojo.DEFAULT_MAVEN_PROJECT_REFID, "not a project");

        BuildException exception = assertThrows(BuildException.class, task::execute);

        assertTrue(exception.getMessage().contains("Maven project reference"));
    }

    private AttachArtifactTask newTask(String projectRefId, Object projectReference) throws IOException {
        MavenProjectHelper projectHelper = newHelperStub();

        Project antProject = new Project();
        antProject.addReference(projectRefId, projectReference);
        antProject.addReference(AntRunMojo.DEFAULT_MAVEN_PROJECT_HELPER_REFID, projectHelper);

        File file = Files.createTempFile(folder, "artifact", "." + TYPE).toFile();

        AttachArtifactTask task = new AttachArtifactTask();
        task.setProject(antProject);
        task.setMavenProjectRefId(projectRefId);
        task.setFile(file);
        task.setClassifier(CLASSIFIER);
        task.setType(TYPE);
        return task;
    }

    private MavenProjectHelper newHelperStub() {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("attachArtifact".equals(method.getName()) && args.length == 4) {
                attachedToProject = (MavenProject) args[0];
                attachedType = (String) args[1];
                attachedClassifier = (String) args[2];
                attachedFile = (File) args[3];
            }
            return null;
        };
        return (MavenProjectHelper)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {MavenProjectHelper.class}, handler);
    }
}
