@file:Suppress("FunctionName")

package com.max.reader.app

import com.max.reader.app.env.Environment
import com.max.reader.app.serialization.PersistentListSerializer
import com.oliynick.max.tea.core.Initializer
import com.oliynick.max.tea.core.component.shareIn
import com.oliynick.max.tea.core.component.states
import com.oliynick.max.tea.core.debug.component.Component
import com.oliynick.max.tea.core.debug.component.URL
import com.oliynick.max.tea.core.debug.gson.GsonSerializer
import com.oliynick.max.tea.core.debug.protocol.ComponentId
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.Flow

fun Environment.AppComponent(
    initializer: Initializer<AppState, Command>
): (Flow<Message>) -> Flow<AppState> {

    suspend fun resolve(command: Command) = this.resolve(command)

    fun update(
        message: Message,
        state: AppState
    ) = this.update(message, state)

    // todo state persistence

    return Component(
        ComponentId("News Reader App"),
        initializer,
        ::resolve,
        ::update,
        AppGsonSerializer()
    ) {
        serverSettings {
            url = URL(host = "10.0.2.2")
        }
    }.shareIn(this).states()
}

private fun AppGsonSerializer() = GsonSerializer {
    registerTypeHierarchyAdapter(PersistentList::class.java, PersistentListSerializer)
}
