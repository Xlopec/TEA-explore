import org.gradle.kotlin.dsl.kotlin
import org.gradle.kotlin.dsl.version
import org.gradle.plugin.use.PluginDependenciesSpec
import org.gradle.plugin.use.PluginDependencySpec

fun PluginDependenciesSpec.kotlin(): PluginDependencySpec =
    kotlin("jvm") version kotlinVersion

fun PluginDependenciesSpec.detekt(): PluginDependencySpec =
    id("io.gitlab.arturbosch.detekt").version(BuildPlugins.Versions.detektVersion)

fun PluginDependenciesSpec.dokka(): PluginDependencySpec =
    id("org.jetbrains.dokka").version(BuildPlugins.Versions.dokkaVersion)

fun PluginDependenciesSpec.bintray(): PluginDependencySpec =
    id("com.jfrog.bintray").version(BuildPlugins.Versions.bintrayVersion)

fun PluginDependenciesSpec.intellij(): PluginDependencySpec =
    id("org.jetbrains.intellij").version(BuildPlugins.Versions.intellijVersion)