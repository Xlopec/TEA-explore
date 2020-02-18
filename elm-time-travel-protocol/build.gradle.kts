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

dependencies {

    api(Libraries.coroutinesCore)
    api("com.google.code.gson:gson:2.8.6")

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(Libraries.kotlinStdLib)
    implementation(Libraries.kotlinReflect)

    implementation(Libraries.ktorWebsockets)
    implementation(Libraries.ktorOkHttp)

    testImplementation(project(path = ":elm-core-test", configuration = "default"))

}

