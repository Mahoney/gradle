/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.cachemanager;

import org.apache.ivy.core.cache.CacheDownloadOptions;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.repository.ArtifactResourceResolver;
import org.apache.ivy.plugins.repository.ResourceDownloader;
import org.apache.ivy.plugins.resolver.util.ResolvedResource;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;

import java.text.ParseException;

/**
 * This is a transitional interface for moving away from the Ivy RepositoryCacheManager.
 */
public interface RepositoryArtifactCache {
    boolean isLocal();

    public ModuleDescriptor cacheModuleDescriptor(ExternalResourceResolver resolver,
            ResolvedResource orginalMetadataRef, DependencyDescriptor dd,
            Artifact requestedMetadataArtifact,  ResourceDownloader downloader) throws ParseException;

    public EnhancedArtifactDownloadReport download(
            Artifact artifact,
            ArtifactResourceResolver resourceResolver,
            ResourceDownloader resourceDownloader,
            CacheDownloadOptions options);
}
