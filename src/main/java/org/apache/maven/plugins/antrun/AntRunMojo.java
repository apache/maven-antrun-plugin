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
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.taskdefs.Typedef;
import org.apache.tools.ant.types.Path;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;

/**
 * <p>
 * Maven AntRun Mojo.
 * <p>
 * This plugin provides the capability of calling Ant tasks from a POM by running the nested Ant tasks inside the
 * &lt;target/&gt; parameter. It is encouraged to move the actual tasks to a separate build.xml file and call that file
 * with an &lt;ant/&gt; task.
 *
 * @author <a href="mailto:kenney@apache.org">Kenney Westerhof</a>
 * @author <a href="mailto:vincent.siveton@gmail.com">Vincent Siveton</a>
 */
@Mojo(name = "run", threadSafe = true, requiresDependencyResolution = ResolutionScope.TEST)
public class AntRunMojo extends AbstractMojo {

    /**
     * The prefix of all refid used by the plugin.
     */
    public static final String MAVEN_REFID_PREFIX = "maven.";

    /**
     * The refid used to store the Maven project object in the Ant build. If this reference is retrieved in a custom
     * task, note that this will be a clone of the Maven project, and not the project itself, when the task is called
     * through an <code>ant</code> task.
     */
    public static final String DEFAULT_MAVEN_PROJECT_REFID = MAVEN_REFID_PREFIX + "project";

    /**
     * The refid used to store an object of type {@link MavenAntRunProject} containing the Maven project object in the
     * Ant build. This is useful when a custom task needs to change the Maven project, because, unlike
     * {@link #DEFAULT_MAVEN_PROJECT_REFID}, this makes sure to reference the same instance of the Maven project in all
     * cases.
     */
    public static final String DEFAULT_MAVEN_PROJECT_REF_REFID = MAVEN_REFID_PREFIX + "project.ref";

    /**
     * The refid used to store the Maven project object in the Ant build.
     */
    public static final String DEFAULT_MAVEN_PROJECT_HELPER_REFID = MAVEN_REFID_PREFIX + "project.helper";

    /**
     * The default target name.
     */
    public static final String DEFAULT_ANT_TARGET_NAME = "main";

    /**
     * The default encoding to use for the generated Ant build.
     */
    public static final String UTF_8 = "UTF-8";

    /**
     * The path to The XML file containing the definition of the Maven tasks.
     */
    public static final String ANTLIB = "org/apache/maven/ant/tasks/antlib.xml";

    /**
     * The URI which defines the built in Ant tasks
     */
    public static final String TASK_URI = "antlib:org.apache.maven.ant.tasks";

    /**
     * The Maven project object
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject mavenProject;

    /**
     * The Maven session object
     */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * The Maven project helper object
     */
    @Component
    private MavenProjectHelper projectHelper;

    /**
     * The plugin dependencies.
     */
    @Parameter(property = "plugin.artifacts", required = true, readonly = true)
    private List<Artifact> pluginArtifacts;

    /**
     * The local Maven repository
     */
    @Parameter(property = "localRepository", readonly = true)
    protected ArtifactRepository localRepository;

    /**
     * String to prepend to project and dependency property names.
     *
     * @since 1.4
     */
    @Parameter(defaultValue = "")
    private String propertyPrefix;

    /**
     * Maven will look in the target-tag for the namespace of <code>http://maven.apache.org/ANTRUN</code>
     * or <code>antlib:org.apache.maven.ant.tasks</code>
     *
     * <pre>
     *   &lt;configuration&gt;
     *     &lt;target xmlns:mvn="http://maven.apache.org/ANTRUN"&gt;
     *       &lt;mvn:attachartifact/&gt;
     *       &lt;mvn:dependencyfilesets/&gt;
     *     &lt;/target&gt;
     *   &lt;/configuration&gt;
     * </pre>
     *
     * @deprecated only here for backwards compatibility
     * @since 1.5
     */
    @Deprecated
    @Parameter
    private String customTaskPrefix;

    /**
     * The name of a property containing the list of all dependency versions. This is used for the removing the versions
     * from the filenames.
     */
    @Parameter(defaultValue = "maven.project.dependencies.versions")
    private String versionsPropertyName;

    /**
     * The XML for the Ant task. You can add anything you can add between &lt;target&gt; and &lt;/target&gt; in a
     * build.xml.
     *
     * @deprecated Use {@link #target} instead. For version 3.0.0, this parameter is only defined to break the build if
     *             you use it!
     */
    @Deprecated
    @Parameter
    private PlexusConfiguration tasks;

    /**
     * The XML for the Ant target. You can add anything you can add between &lt;target&gt; and &lt;/target&gt; in a
     * build.xml.
     *
     * @since 1.5
     */
    @Parameter
    private PlexusConfiguration target;

    /**
     * This folder is added to the list of those folders containing source to be compiled. Use this if your Ant script
     * generates source code.
     *
     * @deprecated Use the <code>build-helper-maven-plugin</code> to bind source directories. For version 3.0.0, this
     *             parameter is only defined to break the build if you use it!
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @Parameter(property = "sourceRoot")
    private File sourceRoot;

    /**
     * This folder is added to the list of those folders containing source to be compiled for testing. Use this if your
     * Ant script generates test source code.
     *
     * @deprecated Use the <code>build-helper-maven-plugin</code> to bind test source directories. For version 3.0.0,
     *             this parameter is only defined to break the build if you use it!
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @Parameter(property = "testSourceRoot")
    private File testSourceRoot;

    /**
     * Specifies whether the Antrun execution should be skipped.
     *
     * @since 1.7
     */
    @Parameter(property = "maven.antrun.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Specifies whether the Ant properties should be propagated to the Maven properties.
     *
     * @since 1.7
     */
    @Parameter(defaultValue = "false")
    private boolean exportAntProperties;

    /**
     * Specifies whether a failure in the Ant build leads to a failure of the Maven build. If this value is
     * {@code false}, the Maven build will proceed even if the Ant build fails. If it is {@code true}, then the Maven
     * build fails if the Ant build fails.
     *
     * @since 1.7
     */
    @Parameter(defaultValue = "true")
    private boolean failOnError;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        checkDeprecatedParameterUsage(tasks, "tasks", "target");
        checkDeprecatedParameterUsage(sourceRoot, "sourceRoot", "the build-helper-maven-plugin");
        checkDeprecatedParameterUsage(testSourceRoot, "testSourceRoot", "the build-helper-maven-plugin");
        if (skip) {
            getLog().info("Skipping Antrun execution");
            return;
        }

        if (target == null) {
            getLog().info("No Ant target defined - SKIPPED");
            return;
        }

        if (propertyPrefix == null) {
            propertyPrefix = "";
        }

        String antTargetName = target.getAttribute("name", DEFAULT_ANT_TARGET_NAME);
        target.setAttribute("name", antTargetName);

        Project antProject = new Project();
        antProject.addBuildListener(getConfiguredBuildLogger());
        try {
            File antBuildFile = writeTargetToProjectFile(antTargetName);
            ProjectHelper.configureProject(antProject, antBuildFile);
            antProject.init();

            antProject.setBaseDir(mavenProject.getBasedir());

            addAntProjectReferences(mavenProject, antProject);
            initMavenTasks(antProject);

            // The Ant project needs actual properties vs. using expression evaluator when calling an external build
            // file.
            copyProperties(mavenProject, antProject);

            getLog().info("Executing tasks");
            antProject.executeTarget(antTargetName);
            getLog().info("Executed tasks");

            copyProperties(antProject, mavenProject);
        } catch (BuildException e) {
            StringBuilder sb = new StringBuilder();
            sb.append("An Ant BuildException has occurred: ").append(e.getMessage());
            String fragment = findFragment(e);
            if (fragment != null) {
                sb.append("\n").append(fragment);
            }
            if (!failOnError) {
                getLog().info(sb.toString(), e);
                return; // do not register roots.
            } else {
                throw new MojoExecutionException(sb.toString(), e);
            }
        } catch (Throwable e) {
            throw new MojoExecutionException("Error executing Ant tasks: " + e.getMessage(), e);
        }
    }

    private void checkDeprecatedParameterUsage(Object parameter, String name, String replacement)
            throws MojoFailureException {
        if (parameter != null) {
            throw new MojoFailureException("You are using '" + name + "' which has been removed"
                    + " from the maven-antrun-plugin. Please use '" + replacement
                    + "' and refer to the >>Major Version Upgrade to version 3.0.0<< " + "on the plugin site.");
        }
    }

    private DefaultLogger getConfiguredBuildLogger() {
        DefaultLogger antLogger = new MavenLogger(getLog());
        if (getLog().isDebugEnabled()) {
            antLogger.setMessageOutputLevel(Project.MSG_DEBUG);
        } else if (getLog().isInfoEnabled()) {
            antLogger.setMessageOutputLevel(Project.MSG_INFO);
        } else if (getLog().isWarnEnabled()) {
            antLogger.setMessageOutputLevel(Project.MSG_WARN);
        } else if (getLog().isErrorEnabled()) {
            antLogger.setMessageOutputLevel(Project.MSG_ERR);
        } else {
            antLogger.setMessageOutputLevel(Project.MSG_VERBOSE);
        }
        return antLogger;
    }

    private void addAntProjectReferences(MavenProject mavenProject, Project antProject)
            throws DependencyResolutionRequiredException {
        Path p = new Path(antProject);
        p.setPath(StringUtils.join(mavenProject.getCompileClasspathElements().iterator(), File.pathSeparator));

        /* maven.dependency.classpath it's deprecated as it's equal to maven.compile.classpath */
        antProject.addReference(MAVEN_REFID_PREFIX + "dependency.classpath", p);
        antProject.addReference(MAVEN_REFID_PREFIX + "compile.classpath", p);

        p = new Path(antProject);
        p.setPath(StringUtils.join(mavenProject.getRuntimeClasspathElements().iterator(), File.pathSeparator));
        antProject.addReference(MAVEN_REFID_PREFIX + "runtime.classpath", p);

        p = new Path(antProject);
        p.setPath(StringUtils.join(mavenProject.getTestClasspathElements().iterator(), File.pathSeparator));
        antProject.addReference(MAVEN_REFID_PREFIX + "test.classpath", p);

        /* set maven.plugin.classpath with plugin dependencies */
        antProject.addReference(
                MAVEN_REFID_PREFIX + "plugin.classpath", getPathFromArtifacts(pluginArtifacts, antProject));

        antProject.addReference(DEFAULT_MAVEN_PROJECT_REFID, mavenProject);
        antProject.addReference(DEFAULT_MAVEN_PROJECT_REF_REFID, new MavenAntRunProject(mavenProject));
        antProject.addReference(DEFAULT_MAVEN_PROJECT_HELPER_REFID, projectHelper);
        antProject.addReference(MAVEN_REFID_PREFIX + "local.repository", localRepository);
    }

    /**
     * @param artifacts {@link Artifact} collection.
     * @param antProject {@link Project}
     * @return {@link Path}
     * @throws DependencyResolutionRequiredException In case of a failure.
     */
    private Path getPathFromArtifacts(Collection<Artifact> artifacts, Project antProject)
            throws DependencyResolutionRequiredException {
        if (artifacts == null) {
            return new Path(antProject);
        }

        List<String> list = new ArrayList<>(artifacts.size());
        for (Artifact a : artifacts) {
            File file = a.getFile();
            if (file == null) {
                throw new DependencyResolutionRequiredException(a);
            }
            list.add(file.getPath());
        }

        Path p = new Path(antProject);
        p.setPath(StringUtils.join(list.iterator(), File.pathSeparator));

        return p;
    }

    /**
     * Copy properties from the Maven project to the Ant project.
     *
     * @param mavenProject {@link MavenProject}
     * @param antProject {@link Project}
     */
    public void copyProperties(MavenProject mavenProject, Project antProject) {
        Properties mavenProps = mavenProject.getProperties();
        Properties userProps = session.getUserProperties();
        List<String> allPropertyKeys = new ArrayList<>(mavenProps.stringPropertyNames());
        allPropertyKeys.addAll(userProps.stringPropertyNames());
        for (String key : allPropertyKeys) {
            String value = userProps.getProperty(key, mavenProps.getProperty(key));
            antProject.setProperty(key, value);
        }

        // Set the POM file as the ant.file for the tasks run directly in Maven.
        antProject.setProperty("ant.file", mavenProject.getFile().getAbsolutePath());

        // Add some of the common Maven properties
        getLog().debug("Setting properties with prefix: " + propertyPrefix);
        antProject.setProperty((propertyPrefix + "project.groupId"), mavenProject.getGroupId());
        antProject.setProperty((propertyPrefix + "project.artifactId"), mavenProject.getArtifactId());
        antProject.setProperty((propertyPrefix + "project.name"), mavenProject.getName());
        if (mavenProject.getDescription() != null) {
            antProject.setProperty((propertyPrefix + "project.description"), mavenProject.getDescription());
        }
        antProject.setProperty((propertyPrefix + "project.version"), mavenProject.getVersion());
        antProject.setProperty((propertyPrefix + "project.packaging"), mavenProject.getPackaging());
        antProject.setProperty(
                (propertyPrefix + "project.build.directory"),
                mavenProject.getBuild().getDirectory());
        antProject.setProperty(
                (propertyPrefix + "project.build.outputDirectory"),
                mavenProject.getBuild().getOutputDirectory());
        antProject.setProperty(
                (propertyPrefix + "project.build.testOutputDirectory"),
                mavenProject.getBuild().getTestOutputDirectory());
        antProject.setProperty(
                (propertyPrefix + "project.build.sourceDirectory"),
                mavenProject.getBuild().getSourceDirectory());
        antProject.setProperty(
                (propertyPrefix + "project.build.testSourceDirectory"),
                mavenProject.getBuild().getTestSourceDirectory());
        antProject.setProperty((propertyPrefix + "localRepository"), localRepository.toString());
        antProject.setProperty((propertyPrefix + "settings.localRepository"), localRepository.getBasedir());

        // Add properties for dependency artifacts
        Set<Artifact> depArtifacts = mavenProject.getArtifacts();
        for (Artifact artifact : depArtifacts) {
            String propName = artifact.getDependencyConflictId();

            antProject.setProperty(propertyPrefix + propName, artifact.getFile().getPath());
        }

        // Add a property containing the list of versions for the mapper
        StringBuilder versionsBuffer = new StringBuilder();
        for (Artifact artifact : depArtifacts) {
            versionsBuffer.append(artifact.getVersion()).append(File.pathSeparator);
        }
        antProject.setProperty(versionsPropertyName, versionsBuffer.toString());
    }

    /**
     * Copy properties from the Ant project to the Maven project.
     *
     * @param antProject not null
     * @param mavenProject not null
     * @since 1.7
     */
    public void copyProperties(Project antProject, MavenProject mavenProject) {
        if (!exportAntProperties) {
            return;
        }

        getLog().debug("Propagated Ant properties to Maven properties");
        Hashtable<String, Object> antProps = antProject.getProperties();
        Properties mavenProperties = mavenProject.getProperties();

        for (Map.Entry<String, Object> entry : antProps.entrySet()) {
            String key = entry.getKey();
            if (mavenProperties.getProperty(key) != null) {
                getLog().debug("Ant property '" + key + "=" + mavenProperties.getProperty(key)
                        + "' clashs with an existing Maven property, SKIPPING this Ant property propagation.");
                continue;
            }
            // it is safe to call toString directly since the value cannot be null in Hashtable
            mavenProperties.setProperty(key, entry.getValue().toString());
        }
    }

    /**
     * @param antProject {@link Project}
     */
    public void initMavenTasks(Project antProject) {
        getLog().debug("Initialize Maven Ant Tasks");
        Typedef typedef = new Typedef();
        typedef.setProject(antProject);
        typedef.setResource(ANTLIB);

        if (getTaskPrefix() != null) {
            typedef.setURI(TASK_URI);
        }
        typedef.execute();
    }

    /**
     * Write the Ant target and surrounding tags to a temporary file
     *
     * @throws IOException problem with write to file
     */
    private File writeTargetToProjectFile(String targetName) throws IOException {
        // The fileName should probably use the plugin executionId instead of the targetName
        File buildFile = new File(mavenProject.getBuild().getDirectory(), "antrun/build-" + targetName + ".xml");
        // noinspection ResultOfMethodCallIgnored
        buildFile.getParentFile().mkdirs();

        AntrunXmlPlexusConfigurationWriter xmlWriter = new AntrunXmlPlexusConfigurationWriter();

        String taskPrefix = getTaskPrefix();
        if (taskPrefix != null) {
            // replace namespace as Ant expects it to be
            target.setAttribute("xmlns:" + taskPrefix, TASK_URI);
        }

        xmlWriter.write(target, buildFile, "", targetName);

        return buildFile;
    }

    private String getTaskPrefix() {
        String taskPrefix = this.customTaskPrefix;
        if (taskPrefix == null) {
            for (String name : target.getAttributeNames()) {
                if (name.startsWith("xmlns:") && "http://maven.apache.org/ANTRUN".equals(target.getAttribute(name))) {
                    taskPrefix = name.substring("xmlns:".length());
                    break;
                }
            }
        }
        return taskPrefix;
    }

    /**
     * @param buildException not null
     * @return the fragment XML part where the buildException occurs.
     * @since 1.7
     */
    private String findFragment(BuildException buildException) {
        if (buildException == null
                || buildException.getLocation() == null
                || buildException.getLocation().getFileName() == null) {
            return null;
        }

        File antFile = new File(buildException.getLocation().getFileName());
        if (!antFile.exists()) {
            return null;
        }

        try (LineNumberReader reader = new LineNumberReader(ReaderFactory.newXmlReader(antFile))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (reader.getLineNumber() == buildException.getLocation().getLineNumber()) {
                    return "around Ant part ..." + line.trim() + "... @ "
                            + buildException.getLocation().getLineNumber() + ":"
                            + buildException.getLocation().getColumnNumber() + " in " + antFile.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            getLog().debug(e.getMessage(), e);
        }

        return null;
    }
}
