package org.apache.maven.ant.tasks;

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

import org.apache.maven.ant.tasks.support.SpecificScopesArtifactFilter;
import org.apache.maven.ant.tasks.support.TypesArtifactFilter;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.plugins.antrun.AntRunMojo;
import org.apache.maven.plugins.antrun.taskconfig.DependencyFilesetsConfiguration;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ant task which create a fileset for each dependency in a Maven project, and a
 * fileset containing all selected dependencies.
 *
 * @author pgier
 */
public class DependencyFilesetsTask
    extends Task
{
    /**
     * The project ref Id of the project being used.
     */
    private String mavenProjectId = AntRunMojo.DEFAULT_MAVEN_PROJECT_REFID;

    private DependencyFilesetsConfiguration configuration = new DependencyFilesetsConfiguration();

    /** {@inheritDoc} */
    @Override
    public void execute()
    {
        if ( this.getProject().getReference( mavenProjectId ) == null )
        {
            throw new BuildException( "Maven project reference not found: " + mavenProjectId );
        }

        MavenProject mavenProject = this.getProject().getReference( "maven.project" );

        // Add filesets for depenedency artifacts
        Set<Artifact> depArtifacts = filterArtifacts( mavenProject.getArtifacts() );

        FileSet dependenciesFileSet = new FileSet();
        dependenciesFileSet.setProject( getProject() );
        ArtifactRepository localRepository = getProject().getReference( "maven.local.repository" );
        dependenciesFileSet.setDir( new File( localRepository.getBasedir() ) );

        if ( depArtifacts.isEmpty() )
        {
            // For performance reasons in case of huge local repo, tell Ant to include a single thing, otherwise the
            // whole directory is scanned (even though ** is excluded).
            dependenciesFileSet.createInclude().setName( "." );
            dependenciesFileSet.createExclude().setName( "**" );
        }

        for ( Artifact artifact : depArtifacts )
        {
            String relativeArtifactPath = localRepository.pathOf( artifact );
            dependenciesFileSet.createInclude().setName( relativeArtifactPath );

            String fileSetName = getPrefix() + artifact.getDependencyConflictId();

            FileSet singleArtifactFileSet = new FileSet();
            singleArtifactFileSet.setProject( getProject() );
            singleArtifactFileSet.setFile( artifact.getFile() );
            getProject().addReference( fileSetName, singleArtifactFileSet );
        }

        getProject().addReference( ( getPrefix() + getProjectDependenciesId() ), dependenciesFileSet );
    }

    /**
     * @return {@link #mavenProjectId}
     */
    public String getMavenProjectId()
    {
        return mavenProjectId;
    }

    /**
     * @param mavenProjectId {@link #mavenProjectId}
     */
    public void setMavenProjectId( String mavenProjectId )
    {
        this.mavenProjectId = mavenProjectId;
    }

    /**
     * @return prefix Prefix to be added to each of the dependency filesets
     */
    public String getPrefix()
    {
        String prefix = configuration.getPrefix();
        if ( prefix == null )
        {
            prefix = "";
        }
        return prefix;
    }

    /**
     * Prefix to be added to each of the dependency filesets. Default is empty string.
     * @param prefix String to prepend to all fileset IDs.
     */
    public void setPrefix( String prefix )
    {
        this.configuration.setPrefix( prefix );
    }

    /**
     * @return types Comma separated list of artifact types to include.
     */
    public String getTypes()
    {
        return this.configuration.getTypes();
    }

    /**
     * @param types Comma separated list of artifact types to include.
     */
    public void setTypes( String types )
    {
        this.configuration.setTypes( types );
    }

    /**
     * @return scopes Comma separated list of artifact scopes to include.
     */
    public String getScopes()
    {
        return this.configuration.getScopes();
    }

    /**
     * @param scopes Comma separated list of artifact scopes to include.
     */
    public void setScopes( String scopes )
    {
        this.configuration.setScopes( scopes );
    }

    /**
     * @return RefId for the fileset containing all project dependencies - default <code>maven.project.dependencies</code>
     */
    public String getProjectDependenciesId()
    {
        return this.configuration.getProjectDependenciesId();
    }

    /**
     * @param projectDependenciesId RefId for the fileset containing all project dependencies
     */
    public void setProjectDependenciesId( String projectDependenciesId )
    {
        this.configuration.setProjectDependenciesId( projectDependenciesId );
    }

    /**
     * Filter a set of artifacts using the scopes and type filters.
     *
     * @param artifacts {@link Artifact} set.
     * @return The set of filtered artifacts.
     */
    public Set<Artifact> filterArtifacts( Set<Artifact> artifacts )
    {
        String scopes = getScopes();
        if ( scopes == null )
        {
            scopes = "";
        }
        
        String types = getTypes();
        if ( types == null )
        {
            types = "";
        }

        if ( "".equals( scopes ) && "".equals( types ) )
        {
            return artifacts;
        }

        AndArtifactFilter filter = new AndArtifactFilter();
        if ( !"".equals( scopes ) )
        {
            filter.add( new SpecificScopesArtifactFilter( getScopes() ) );
        }
        if ( !"".equals( types ) )
        {
            filter.add( new TypesArtifactFilter( getTypes() ) );
        }

        Set<Artifact> artifactsResult = new LinkedHashSet<>();
        for ( Artifact artifact : artifacts )
        {
            if ( filter.include( artifact ) )
            {
                artifactsResult.add( artifact );
            }
        }
        return artifactsResult;
    }
}
