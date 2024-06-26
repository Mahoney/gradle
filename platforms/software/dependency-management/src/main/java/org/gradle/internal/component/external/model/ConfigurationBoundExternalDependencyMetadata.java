/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.component.external.model;

import com.google.common.base.Objects;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DefaultProjectDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveState;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * A `ModuleDependencyMetadata` implementation that is backed by an `ExternalDependencyDescriptor` bound to a particular
 * source `ConfigurationMetadata`. The reason for this is that the Ivy and Maven dependency descriptors resolve target components
 * differently based on the configuration that they are sourced from.
 */
public class ConfigurationBoundExternalDependencyMetadata implements ModuleDependencyMetadata {
    private final ConfigurationMetadata configuration;
    private final ModuleComponentIdentifier componentId;
    private final ExternalDependencyDescriptor dependencyDescriptor;
    private final String reason;
    private final boolean isTransitive;
    private final boolean isConstraint;
    private final boolean isEndorsing;
    private final List<IvyArtifactName> artifacts;

    // TODO: This is likely misnamed now. We should evaluate whether we can remove this flag.
    private final boolean alwaysUseAttributeMatching;

    private ConfigurationBoundExternalDependencyMetadata(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, ExternalDependencyDescriptor dependencyDescriptor, boolean alwaysUseAttributeMatching, @Nullable String reason, boolean endorsing) {
        this(configuration,
            componentId,
            dependencyDescriptor,
            alwaysUseAttributeMatching,
            reason,
            endorsing,
            dependencyDescriptor.getConfigurationArtifacts(configuration)
        );
    }

    private ConfigurationBoundExternalDependencyMetadata(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, ExternalDependencyDescriptor dependencyDescriptor, boolean alwaysUseAttributeMatching, @Nullable String reason, boolean endorsing, List<IvyArtifactName> artifacts) {
        this.configuration = configuration;
        this.componentId = componentId;
        this.dependencyDescriptor = dependencyDescriptor;
        this.alwaysUseAttributeMatching = alwaysUseAttributeMatching;
        this.reason = reason;
        this.isTransitive = dependencyDescriptor.isTransitive();
        this.isConstraint = dependencyDescriptor.isConstraint();
        this.isEndorsing = endorsing;
        this.artifacts = artifacts;
    }

    public ConfigurationBoundExternalDependencyMetadata(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, ExternalDependencyDescriptor dependencyDescriptor, boolean alwaysUseAttributeMatching) {
        this(configuration, componentId, dependencyDescriptor, alwaysUseAttributeMatching, null, false);
    }

    public ConfigurationBoundExternalDependencyMetadata(ConfigurationMetadata configuration, ModuleComponentIdentifier componentId, ExternalDependencyDescriptor dependencyDescriptor) {
        this(configuration, componentId, dependencyDescriptor, false, null, false);
    }

    public ExternalDependencyDescriptor getDependencyDescriptor() {
        return dependencyDescriptor;
    }

    /**
     * Choose a set of target configurations based on: a) the consumer attributes (with associated schema) and b) the target component.
     *
     * Use attribute matching to choose a single variant when the target component has variants,
     * otherwise revert to legacy selection of target configurations.
     */
    @Override
    public GraphVariantSelectionResult selectVariants(GraphVariantSelector variantSelector, ImmutableAttributes consumerAttributes, ComponentGraphResolveState targetComponentState, AttributesSchemaInternal consumerSchema, Collection<? extends Capability> explicitRequestedCapabilities) {
        if (targetComponentState.getCandidatesForGraphVariantSelection().isUseVariants()) {
            VariantGraphResolveState selected = variantSelector.selectByAttributeMatching(
                consumerAttributes,
                explicitRequestedCapabilities,
                targetComponentState,
                consumerSchema,
                getArtifacts()
            );
            return new GraphVariantSelectionResult(Collections.singletonList(selected), true);
        }

        // Fallback to legacy variant selection for target components without variants.
        if (alwaysUseAttributeMatching) {
            VariantGraphResolveState selected = variantSelector.selectLegacyConfiguration(consumerAttributes, targetComponentState, consumerSchema);
            return new GraphVariantSelectionResult(Collections.singletonList(selected), false);
        }

        return dependencyDescriptor.selectLegacyConfigurations(componentId, configuration, targetComponentState, variantSelector.getFailureProcessor());
    }

    @Override
    public List<IvyArtifactName> getArtifacts() {
        return artifacts;
    }

    @Override
    public List<ExcludeMetadata> getExcludes() {
        return dependencyDescriptor.getConfigurationExcludes(configuration.getHierarchy());
    }

    @Override
    public DependencyMetadata withTarget(ComponentSelector target) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getModuleIdentifier(), moduleTarget.getVersionConstraint(), moduleTarget.getAttributes(), moduleTarget.getRequestedCapabilities());
            if (newSelector.equals(getSelector())) {
                return this;
            }
            return withRequested(newSelector);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    @Override
    public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
        if (target instanceof ModuleComponentSelector) {
            ModuleComponentSelector moduleTarget = (ModuleComponentSelector) target;
            ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(moduleTarget.getModuleIdentifier(), moduleTarget.getVersionConstraint(), moduleTarget.getAttributes(), moduleTarget.getRequestedCapabilities());
            if (newSelector.equals(getSelector()) && getArtifacts().equals(artifacts)) {
                return this;
            }
            return withRequestedAndArtifacts(newSelector, artifacts);
        } else if (target instanceof ProjectComponentSelector) {
            ProjectComponentSelector projectTarget = (ProjectComponentSelector) target;
            return new DefaultProjectDependencyMetadata(projectTarget, this);
        } else {
            throw new IllegalArgumentException("Unexpected selector provided: " + target);
        }
    }

    @Override
    public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
        ModuleComponentSelector selector = getSelector();
        if (requestedVersion.equals(selector.getVersionConstraint())) {
            return this;
        }
        ModuleComponentSelector newSelector = DefaultModuleComponentSelector.newSelector(selector.getModuleIdentifier(), requestedVersion, selector.getAttributes(), selector.getRequestedCapabilities());
        return withRequested(newSelector);
    }

    @Override
    public ModuleDependencyMetadata withReason(String reason) {
        if (Objects.equal(reason, this.getReason())) {
            return this;
        }
        return new ConfigurationBoundExternalDependencyMetadata(configuration, componentId, dependencyDescriptor, alwaysUseAttributeMatching, reason, isEndorsing);
    }

    @Override
    public ModuleDependencyMetadata withEndorseStrictVersions(boolean endorse) {
        if (this.isEndorsing == endorse) {
            return this;
        }
        return new ConfigurationBoundExternalDependencyMetadata(configuration, componentId, dependencyDescriptor, alwaysUseAttributeMatching, reason, endorse);
    }

    public ConfigurationBoundExternalDependencyMetadata withDescriptor(ExternalDependencyDescriptor descriptor) {
        return new ConfigurationBoundExternalDependencyMetadata(configuration, componentId, descriptor, alwaysUseAttributeMatching, reason, isEndorsing);
    }

    private ModuleDependencyMetadata withRequested(ModuleComponentSelector newSelector) {
        ExternalDependencyDescriptor newDelegate = dependencyDescriptor.withRequested(newSelector);
        return new ConfigurationBoundExternalDependencyMetadata(configuration, componentId, newDelegate, alwaysUseAttributeMatching, reason, isEndorsing);
    }

    private ModuleDependencyMetadata withRequestedAndArtifacts(ModuleComponentSelector newSelector, List<IvyArtifactName> artifacts) {
        ExternalDependencyDescriptor newDelegate = dependencyDescriptor.withRequested(newSelector);
        return new ConfigurationBoundExternalDependencyMetadata(configuration, componentId, newDelegate, alwaysUseAttributeMatching, reason, isEndorsing, artifacts);
    }

    @Override
    public ModuleComponentSelector getSelector() {
        return dependencyDescriptor.getSelector();
    }

    @Override
    public boolean isChanging() {
        return dependencyDescriptor.isChanging();
    }

    @Override
    public boolean isTransitive() {
        return isTransitive;
    }

    @Override
    public boolean isConstraint() {
        return isConstraint;
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return isEndorsing;
    }

    @Nullable
    @Override
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return dependencyDescriptor.toString();
    }
}
