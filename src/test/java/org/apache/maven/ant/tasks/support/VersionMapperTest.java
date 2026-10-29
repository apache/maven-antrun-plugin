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
package org.apache.maven.ant.tasks.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Test class for {@link VersionMapper}.
 */
class VersionMapperTest {

    private VersionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new VersionMapper();
        mapper.setTo("flatten");
    }

    /**
     * The trailing version segment of the filename is stripped.
     */
    @Test
    void stripsTrailingVersion() {
        mapper.setFrom("1.0");
        assertArrayEquals(new String[] {"artifactId.jar"}, mapper.mapFileName("artifactId-1.0.jar"));
    }

    /**
     * The version segment before the classifier is stripped, keeping the classifier.
     */
    @Test
    void stripsVersionBeforeClassifier() {
        mapper.setFrom("1.0");
        assertArrayEquals(new String[] {"artifactId-jre.jar"}, mapper.mapFileName("artifactId-1.0-jre.jar"));
    }

    /**
     * A filename starting with the version must not throw a {@link StringIndexOutOfBoundsException}.
     */
    @Test
    void versionAtStartOfFilenameDoesNotThrow() {
        mapper.setFrom("1.0");
        assertArrayEquals(new String[] {"1.0.jar"}, mapper.mapFileName("1.0.jar"));
    }

    /**
     * When the version occurs both inside the artifact id and as the trailing version segment, only the trailing
     * segment is stripped.
     */
    @Test
    void stripsOnlyTrailingOccurrenceOfVersion() {
        mapper.setFrom("1.0");
        assertArrayEquals(new String[] {"a-1.0-b.jar"}, mapper.mapFileName("a-1.0-b-1.0.jar"));
    }

    /**
     * A version-like substring that is not a trailing segment is left untouched.
     */
    @Test
    void doesNotStripVersionInsideArtifactId() {
        mapper.setFrom("1.0");
        assertArrayEquals(new String[] {"a-1.0b.jar"}, mapper.mapFileName("a-1.0b.jar"));
    }

    /**
     * The directory part of the filename is preserved unless the mapper is configured to flatten.
     */
    @Test
    void preservesDirectoryPart() {
        mapper.setFrom("1.0");
        mapper.setTo(null);
        assertArrayEquals(
                new String[] {"directory" + java.io.File.separator + "artifactId.jar"},
                mapper.mapFileName("directory" + java.io.File.separator + "artifactId-1.0.jar"));
    }

    /**
     * A filename without any configured version is returned unchanged.
     */
    @Test
    void returnsUnchangedWhenNoVersionMatches() {
        mapper.setFrom("9.9");
        assertArrayEquals(new String[] {"artifactId-1.0.jar"}, mapper.mapFileName("artifactId-1.0.jar"));
    }
}
