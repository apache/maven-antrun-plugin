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
import java.util.HashSet;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugins.antrun.AntRunMojo;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.Resource;
import org.apache.tools.ant.types.ResourceCollection;
import org.apache.tools.ant.types.resources.FileResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

/**
 * Test class for the aggregate dependency fileset of {@link DependencyFilesetsTask}.
 */
class DependencyFilesetsTaskAggregateTest {

    private static final String LOCAL_REPOSITORY_REFID = "maven.local.repository";

    private static final String DEFAULT_DEPENDENCIES_REFID = "maven.project.dependencies";

    @TempDir
    Path folder;

    /**
     * The aggregate {@code maven.project.dependencies} resource collection must be assembled from the actual resolved
     * artifact files, so that artifacts located outside the local repository (e.g. reactor inter-module dependencies)
     * are not silently omitted.
     *
     * @throws IOException In case of problems
     */
    @Test
    void aggregateIncludesArtifactsOutsideLocalRepository() throws IOException {
        Path localRepositoryDir = Files.createDirectories(folder.resolve("local-repository"));
        Path reactorDirectory = Files.createDirectories(folder.resolve("reactor-module"));

        File localArtifactFile =
                localRepositoryDir.resolve("org/example/artX/1.0/artX-1.0.jar").toFile();
        Files.createDirectories(localArtifactFile.getParentFile().toPath());
        Files.write(localArtifactFile.toPath(), new byte[0]);

        File reactorArtifactFile = reactorDirectory.resolve("artY-1.0.jar").toFile();
        Files.write(reactorArtifactFile.toPath(), new byte[0]);

        MavenProject mavenProject = new MavenProject();
        Set<Artifact> artifacts = new HashSet<>();
        artifacts.add(newArtifact("org.example", "artX", localArtifactFile));
        artifacts.add(newArtifact("com.example", "artY", reactorArtifactFile));
        mavenProject.setArtifacts(artifacts);

        Project antProject = new Project();
        antProject.addReference(AntRunMojo.DEFAULT_MAVEN_PROJECT_REFID, mavenProject);
        antProject.addReference(LOCAL_REPOSITORY_REFID, newRepositoryStub(localRepositoryDir));

        DependencyFilesetsTask task = new DependencyFilesetsTask();
        task.setProject(antProject);

        task.execute();

        ResourceCollection aggregate = (ResourceCollection) antProject.getReference(DEFAULT_DEPENDENCIES_REFID);
        Set<File> files = new HashSet<>();
        for (Resource resource : aggregate) {
            files.add(((FileResource) resource).getFile());
        }
        assertThat(files, hasItem(localArtifactFile));
        assertThat(files, hasItem(reactorArtifactFile));
    }

    private Artifact newArtifact(String groupId, String artifactId, File file) {
        Artifact artifact = new DefaultArtifact(
                groupId, artifactId, "1.0", Artifact.SCOPE_COMPILE, "jar", null, new DefaultArtifactHandler("jar"));
        artifact.setFile(file);
        return artifact;
    }

    private ArtifactRepository newRepositoryStub(Path basedir) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "pathOf":
                    return artifactPath((Artifact) args[0]);
                case "getBasedir":
                    return basedir.toString();
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
