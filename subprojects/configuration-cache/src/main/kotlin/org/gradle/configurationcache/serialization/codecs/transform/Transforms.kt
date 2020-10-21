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

package org.gradle.configurationcache.serialization.codecs.transform

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.transform.ArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultArtifactTransformDependencies
import org.gradle.api.internal.artifacts.transform.DefaultExtraExecutionGraphDependenciesResolverFactory
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependencies
import org.gradle.api.internal.artifacts.transform.TransformUpstreamDependenciesResolver
import org.gradle.api.internal.artifacts.transform.Transformation
import org.gradle.api.internal.artifacts.transform.TransformationNode
import org.gradle.api.internal.artifacts.transform.TransformationStep
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.internal.Try


sealed class TransformDependencies {
    abstract fun recreate(): TransformUpstreamDependenciesResolver

    object NotRequired : TransformDependencies() {
        override fun recreate(): TransformUpstreamDependenciesResolver {
            return DefaultExtraExecutionGraphDependenciesResolverFactory.NO_DEPENDENCIES_RESOLVER
        }
    }

    class FileDependencies(val files: FileCollection) : TransformDependencies() {
        override fun recreate(): TransformUpstreamDependenciesResolver {
            return FixedDependenciesResolver(DefaultArtifactTransformDependencies(files))
        }
    }
}


object TransformDependenciesCodec : Codec<TransformDependencies> {
    override suspend fun WriteContext.encode(value: TransformDependencies) {
        if (value is TransformDependencies.FileDependencies) {
            writeBoolean(true)
            write(value.files)
        } else {
            writeBoolean(false)
        }
    }

    override suspend fun ReadContext.decode(): TransformDependencies {
        return if (readBoolean()) {
            return TransformDependencies.FileDependencies(read() as FileCollection)
        } else {
            TransformDependencies.NotRequired
        }
    }
}


fun unpackTransformation(transformation: Transformation, dependenciesResolver: TransformUpstreamDependenciesResolver): List<TransformDependencies> {
    val dependencies = mutableListOf<TransformDependencies>()
    transformation.visitTransformationSteps {
        dependencies.add(transformDependencies(this, dependenciesResolver.dependenciesFor(this)))
    }
    return dependencies
}


fun transformDependencies(transformation: TransformationStep, upstreamDependencies: TransformUpstreamDependencies): TransformDependencies {
    return if (transformation.requiresDependencies()) {
        TransformDependencies.FileDependencies(upstreamDependencies.selectedArtifacts())
    } else {
        TransformDependencies.NotRequired
    }
}


fun transformDependencies(node: TransformationNode): TransformDependencies {
    return transformDependencies(node.transformationStep, node.upstreamDependencies)
}


class FixedDependenciesResolver(private val dependencies: ArtifactTransformDependencies) : TransformUpstreamDependenciesResolver, TransformUpstreamDependencies {
    override fun dependenciesFor(transformationStep: TransformationStep): TransformUpstreamDependencies {
        return this
    }

    override fun visitDependencies(context: TaskDependencyResolveContext) {
        throw IllegalStateException("Should not be called")
    }

    override fun selectedArtifacts(): FileCollection {
        return dependencies.files
    }

    override fun computeArtifacts(): Try<ArtifactTransformDependencies> {
        return Try.successful(dependencies)
    }
}
