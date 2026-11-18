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
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Test class for {@link AntRunMojo#copyProperties(MavenProject, Project)}.
 */
class AntRunMojoCopyPropertiesTest {

    @TempDir
    Path tempDir;

    private AntRunMojo mojo;

    private MavenProject mavenProject;

    private Project antProject;

    @BeforeEach
    void setUp() throws Exception {
        Model model = new Model();
        model.setGroupId("org.example");
        model.setArtifactId("test-project");
        model.setVersion("1.0");
        model.setPackaging("jar");
        Build build = new Build();
        build.setDirectory("target");
        build.setOutputDirectory("target/classes");
        build.setTestOutputDirectory("target/test-classes");
        build.setSourceDirectory("src/main/java");
        build.setTestSourceDirectory("src/test/java");
        model.setBuild(build);
        mavenProject = new MavenProject(model);
        mavenProject.setFile(Files.createTempFile(tempDir, "pom", ".xml").toFile());

        MavenExecutionRequest request = new DefaultMavenExecutionRequest();
        MavenExecutionResult result = new DefaultMavenExecutionResult();
        MavenSession session = new MavenSession(null, request, result, mavenProject);

        mojo = new AntRunMojo(stub(MavenProjectHelper.class));
        setField(mojo, "session", session);
        setField(mojo, "propertyPrefix", "");
        setField(mojo, "versionsPropertyName", "maven.project.dependencies.versions");
        mojo.localRepository = stub(ArtifactRepository.class);
        mojo.setLog(new SystemStreamLog());

        antProject = new Project();
    }

    @Test
    void copyPropertiesSkipsArtifactWithoutResolvedFile() throws Exception {
        File resolvedFile = Files.createTempFile(tempDir, "resolved", ".jar").toFile();
        Artifact resolved = artifact("org.example", "resolved-artifact", resolvedFile);
        Artifact unresolved = artifact("org.example", "unresolved-artifact", null);
        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(resolved);
        artifacts.add(unresolved);
        mavenProject.setArtifacts(artifacts);

        mojo.copyProperties(mavenProject, antProject);

        assertEquals(resolvedFile.getAbsolutePath(), antProject.getProperty(resolved.getDependencyConflictId()));
        assertNull(antProject.getProperty(unresolved.getDependencyConflictId()));
    }

    private static Artifact artifact(String groupId, String artifactId, File file) {
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, "1.0", "compile", "jar", null, new DefaultArtifactHandler("jar"));
        if (file != null) {
            artifact.setFile(file);
        }
        return artifact;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                AntRunMojoCopyPropertiesTest.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "toString":
                            return "local-repository";
                        case "getBasedir":
                            return "local-repository";
                        case "getUrl":
                            return "file:///local-repository";
                        case "getId":
                            return "local";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        return null;
    }
}
