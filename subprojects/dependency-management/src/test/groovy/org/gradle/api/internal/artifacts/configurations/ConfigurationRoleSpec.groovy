/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations

import spock.lang.Specification

class ConfigurationRoleSpec extends Specification {
    def "roles can describe themselves #role"() {
        expect:
        assertDescriptionContains(role, usages)

        where:
        role                                            || usages
        ConfigurationRoles.LEGACY                       || [UsageDescriber.CONSUMABLE, UsageDescriber.RESOLVABLE, UsageDescriber.DECLARABLE_AGAINST]
        ConfigurationRoles.INTENDED_CONSUMABLE          || [UsageDescriber.CONSUMABLE]
        ConfigurationRoles.INTENDED_RESOLVABLE          || [UsageDescriber.RESOLVABLE]
        ConfigurationRoles.INTENDED_RESOLVABLE_BUCKET   || [UsageDescriber.RESOLVABLE, UsageDescriber.DECLARABLE_AGAINST]
        ConfigurationRoles.INTENDED_BUCKET              || [UsageDescriber.DECLARABLE_AGAINST]
        ConfigurationRoles.DEPRECATED_CONSUMABLE        || [UsageDescriber.CONSUMABLE, deprecatedFor(UsageDescriber.RESOLVABLE), deprecatedFor(UsageDescriber.DECLARABLE_AGAINST)]
        ConfigurationRoles.DEPRECATED_RESOLVABLE        || [UsageDescriber.RESOLVABLE, deprecatedFor(UsageDescriber.CONSUMABLE), deprecatedFor(UsageDescriber.DECLARABLE_AGAINST)]
    }

    def "custom role can't deprecate what it doesn't allow"() {
        when:
        ConfigurationRole.forUsage(consumable, resolvable, declarableAgainst, consumptionDeprecated, resolutionDeprecated, declarationAgainstDeprecated)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Cannot create a role that deprecates a usage that is not allowed'

        where:
        consumable  | resolvable    | declarableAgainst | consumptionDeprecated | resolutionDeprecated  | declarationAgainstDeprecated
        false       | false         | false             | true                  | false                 | false
        false       | false         | false             | false                 | true                  | false
        false       | false         | false             | false                 | false                 | true
    }

    private String deprecatedFor(String usage) {
        return usage + UsageDescriber.describeDeprecation(true)
    }

    private void assertDescriptionContains(ConfigurationRole role, List<String> usages) {
        for (String usage : usages) {
            assert role.describeUsage().contains(usage)
        }
    }
}
