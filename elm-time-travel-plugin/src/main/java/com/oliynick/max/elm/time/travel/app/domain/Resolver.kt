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

package com.oliynick.max.elm.time.travel.app.domain

import com.intellij.ide.util.PropertiesComponent
import com.oliynick.max.elm.core.component.effect
import com.oliynick.max.elm.core.component.sideEffect
import com.oliynick.max.elm.time.travel.app.storage.paths
import com.oliynick.max.elm.time.travel.app.storage.serverSettings
import com.oliynick.max.elm.time.travel.app.transport.ServerHandler
import com.oliynick.max.elm.time.travel.protocol.ApplyMessage
import com.oliynick.max.elm.time.travel.protocol.ApplyState
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel

data class Dependencies(
    val events: Channel<PluginMessage>,
    val manager: ServerHandler,
    val properties: PropertiesComponent
) {
    val exceptions: BroadcastChannel<DoNotifyOperationException> = BroadcastChannel(1)
    val notifications: BroadcastChannel<NotificationMessage> = BroadcastChannel(1)
}

suspend fun Dependencies.resolve(command: PluginCommand): Set<PluginMessage> {
    suspend fun resolve(): Set<PluginMessage> {
        return when (command) {
            is StoreFiles -> command.sideEffect { properties.paths = files }
            is StoreServerSettings -> command.sideEffect { properties.serverSettings = serverSettings }
            is DoStartServer -> command.effect { manager.start(command.settings, events); NotifyStarted }
            DoStopServer -> command.effect { manager.stop(); NotifyStopped }
            is DoApplyCommands -> command.sideEffect { manager(id, ApplyMessage(commands)) }
            is DoNotifyOperationException -> command.sideEffect { exceptions.send(command) }
            is DoApplyState -> command.effect { manager(id, ApplyState(state)); StateReApplied(id, state) }
        }
    }

    return runCatching { resolve().also { messages -> notifications.send(messages.notifications()) } }
        .getOrElse { th -> setOf(NotifyOperationException(th, command)) }
}

private fun Iterable<PluginMessage>.notifications() = filterIsInstance<NotificationMessage>()

private suspend fun BroadcastChannel<NotificationMessage>.send(messages: Iterable<NotificationMessage>) {
    messages.forEach { notification -> send(notification) }
}