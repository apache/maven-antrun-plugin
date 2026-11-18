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
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugins.antrun.AntRunMojo;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test class for {@link DependencyFilesetsTask}.
 */
class DependencyFilesetsTaskTest {

    private static final String CUSTOM_PROJECT_REFID = "maven.project.alt";

    private static final String LOCAL_REPOSITORY_REFID = "maven.local.repository";

    @TempDir
    File folder;

    /**
     * The task must use the {@link MavenProject} registered under the configured {@code mavenProjectId} reference,
     * not a hardcoded one.
     *
     * @throws IOException In case of problems
     */
    @Test
    void honorsConfiguredMavenProjectId() throws IOException {
        Artifact defaultArtifact = newArtifact("org.example", "artX");
        Artifact customArtifact = newArtifact("com.example", "artY");
        MavenProject defaultProject = newProject(defaultArtifact);
        MavenProject customProject = newProject(customArtifact);

        Project antProject = new Project();
        antProject.addReference(AntRunMojo.DEFAULT_MAVEN_PROJECT_REFID, defaultProject);
        antProject.addReference(CUSTOM_PROJECT_REFID, customProject);
        antProject.addReference(LOCAL_REPOSITORY_REFID, newRepositoryStub());

        DependencyFilesetsTask task = new DependencyFilesetsTask();
        task.setProject(antProject);
        task.setMavenProjectId(CUSTOM_PROJECT_REFID);

        task.execute();

        assertNotNull(antProject.getReference(customArtifact.getDependencyConflictId()));
        assertNull(antProject.getReference(defaultArtifact.getDependencyConflictId()));
    }

    /**
     * The task must work without the default {@code maven.project} reference when a custom {@code mavenProjectId}
     * is configured, instead of throwing an NPE.
     *
     * @throws IOException In case of problems
     */
    @Test
    void worksWithoutDefaultMavenProjectReference() throws IOException {
        Artifact customArtifact = newArtifact("com.example", "artY");
        MavenProject customProject = newProject(customArtifact);

        Project antProject = new Project();
        antProject.addReference(CUSTOM_PROJECT_REFID, customProject);
        antProject.addReference(LOCAL_REPOSITORY_REFID, newRepositoryStub());

        DependencyFilesetsTask task = new DependencyFilesetsTask();
        task.setProject(antProject);
        task.setMavenProjectId(CUSTOM_PROJECT_REFID);

        assertDoesNotThrow(task::execute);

        assertNotNull(antProject.getReference(customArtifact.getDependencyConflictId()));
    }

    private MavenProject newProject(Artifact artifact) {
        MavenProject project = new MavenProject();
        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(artifact);
        project.setArtifacts(artifacts);
        return project;
    }

    private Artifact newArtifact(String groupId, String artifactId) throws IOException {
        Artifact artifact = new DefaultArtifact(
                groupId, artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(
                Files.createTempFile(folder.toPath(), artifactId, ".jar").toFile());
        return artifact;
    }

    private ArtifactRepository newRepositoryStub() {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "pathOf":
                    return artifactPath((Artifact) args[0]);
                case "getBasedir":
                    return folder.getAbsolutePath();
                default:
                    return null;
            }
        };
        return (ArtifactRepository)
                Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {ArtifactRepository.class}, handler);
    }

    private static String artifactPath(Artifact artifact) {
        return artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/"
                + artifact.getArtifactId() + "-" + artifact.getVersion() + "." + artifact.getType();
    }
}
