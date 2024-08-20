/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Issue

class ConfigurationCacheCleanupIntegrationTest
    extends AbstractConfigurationCacheIntegrationTest
    implements FileAccessTimeJournalFixture {

    @Issue('https://github.com/gradle/gradle/issues/23957')
    def "cleanup deletes old entries"() {
        given: 'there are two configuration cache entries'
        executer.requireOwnGradleUserHomeDir('needs its own journal')
        executer.requireIsolatedDaemons()
        buildFile '''
            task outdated
            task recent
        '''
        configurationCacheRunNoDaemon 'outdated'
        TestFile outdated = single(subDirsOf(configurationCacheDir))
        configurationCacheRunNoDaemon 'recent'
        TestFile recent = single(subDirsOf(configurationCacheDir) - outdated)

        and: 'they are 8 days old'
        subDirsOf(configurationCacheDir).each { TestFile dir ->
            writeLastFileAccessTimeToJournal dir, daysAgo(8)
        }

        and: 'but one was recently accessed'
        configurationCacheRunNoDaemon 'recent'

        and: 'the last cleanup was 2 days ago'
        writeJournalInceptionTimestamp daysAgo(2)
        gcFile.createFile().lastModified = daysAgo(2)

        expect: 'Gradle to preserve the recent entry and to delete the outdated one'
        boolean recentEntryIsReused = true
        def cc = newConfigurationCacheFixture()
        ConcurrentTestUtil.poll(120, 0.5, 5) {
            configurationCacheRunNoDaemon 'recent'
            recentEntryIsReused &= cc.reused
            assert !outdated.exists()
        }
        recentEntryIsReused

        and:
        def remaining = configurationCacheDir.list() as Set
        def expected = [recent.name, 'gc.properties', 'configuration-cache.lock'] as Set
        expected == remaining
    }

    private void configurationCacheRunNoDaemon(String task) {
        configurationCacheRun task, '--no-daemon'
    }

    private TestFile getGcFile() {
        return configurationCacheDir.file('gc.properties')
    }

    private TestFile getConfigurationCacheDir() {
        return file('.gradle/configuration-cache')
    }

    private static List<TestFile> subDirsOf(TestFile dir) {
        dir.listFiles().findAll { it.directory }
    }

    private static <T> T single(List<T> list) {
        list.with {
            assert size() == 1
            get(0)
        }
    }
}
