
import Libraries.coroutinesCore
import Libraries.coroutinesSwing
import Libraries.immutableCollections
import Libraries.kotlinStdLib
import Libraries.ktorServerCore
import Libraries.ktorServerNetty
import Libraries.ktorServerWebsockets
import TestLibraries.ktorServerTests
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/*
 * Copyright (C) 2019 Maksym Oliinyk.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

intellij {
    version = "2020.1"
    setPlugins("com.intellij.java")
}

tasks.named<PatchPluginXmlTask>("patchPluginXml") {
    setVersion(versionName())
}

tasks.named<PublishTask>("publishPlugin") {
    token(System.getenv("PUBLISH_PLUGIN_TOKEN"))
    channels(*pluginReleaseChannels())
}

sourceSets {
    main {
        resources {
            srcDir("resources")
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(project(path = ":tea-core", configuration = "default"))
    implementation(project(path = ":tea-time-travel-protocol", configuration = "default"))
    implementation(project(path = ":tea-time-travel-adapter-gson", configuration = "default"))

    implementation(kotlinStdLib)

    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation(ktorServerCore)
    implementation(ktorServerNetty)
    implementation(ktorServerWebsockets)
    implementation(coroutinesCore)
    implementation(coroutinesSwing)
    implementation(immutableCollections)

    testImplementation(ktorServerTests)
    testImplementation(project(path = ":tea-test", configuration = "default"))
}