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

import com.oliynick.max.elm.core.component.UpdateWith
import java.util.*

internal fun update(message: PluginMessage, state: PluginState): UpdateWith<PluginState, PluginCommand> {
    return when (message) {
        is UIMessage -> updateForUser(message, state)
        is NotificationMessage -> updateForNotification(message, state)
    }
}

// fixme remove this shi
inline fun <reified R : T, T> toExpected(t: T, crossinline message: () -> Any = { "Unexpected state, required ${R::class} but was $t" }): R {
    require(t is R, message)
    return t
}


fun ComponentDebugState.appendSnapshot(snapshot: Snapshot): ComponentDebugState {
    return copy(snapshots = snapshots + snapshot, currentState = snapshot.state)
}

fun ComponentDebugState.removeSnapshots(ids: Set<UUID>): ComponentDebugState {
    return copy(snapshots = snapshots.filter { snapshot -> !ids.contains(snapshot.id) })
}

fun ComponentDebugState.asPair() = id to this