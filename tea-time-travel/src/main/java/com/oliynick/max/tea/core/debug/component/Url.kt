/*
 * Copyright (C) 2021. Maksym Oliinyk.
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

@file:Suppress("FunctionName")

package com.oliynick.max.tea.core.debug.component

import java.net.URL

/**
 * Url builder
 *
 * This is just a shorthand for
 * ```kotlin
 * URL(protocol, host, port.toInt(), "")
 * ```
 */
public fun URL(
    protocol: String = "http",
    host: String = "localhost",
    port: UInt = 8080U
): URL = URL(protocol, host, port.toInt(), "")