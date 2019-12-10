@file:Suppress("FunctionName")

package com.max.weatherviewer.app

import com.max.weatherviewer.Command
import com.max.weatherviewer.LoadByCriteria
import com.max.weatherviewer.env.Environment
import com.max.weatherviewer.home.FeedLoading
import com.max.weatherviewer.home.LoadCriteria
import com.oliynick.max.elm.core.actor.Component
import com.oliynick.max.elm.core.component.Component
import com.oliynick.max.elm.core.component.Env
import com.oliynick.max.elm.core.component.androidLogger
import com.oliynick.max.elm.time.travel.Component
import com.oliynick.max.elm.time.travel.URL
import protocol.*
import java.net.URL
import java.util.*

fun Environment.appComponent(): Component<Message, State> {

    suspend fun resolve(command: Command) = this.resolve(command)

    fun update(message: Message, state: State) = this.update(message, state)

    val initScreen = FeedLoading(UUID.randomUUID(), LoadCriteria.Query("bitcoin"))

    // todo state persistence
    val componentDependencies = Env(
        State(initScreen),
        ::resolve,
        ::update,
        LoadByCriteria(initScreen.id, initScreen.criteria)
    ) {
        interceptor = androidLogger("News Reader App")
    }

    if (false && isDebug) {

        return Component(ComponentId("News Reader App"), componentDependencies, URL(host = "10.0.2.2")) {
            serverSettings {

                converters {
                    +URLConverter
                }
            }
        }
    }

    return Component(componentDependencies)

}

private object URLConverter : Converter<URL, StringWrapper> {

    override fun from(v: StringWrapper, converters: Converters): URL? = URL(v.value)

    override fun to(t: URL, converters: Converters): StringWrapper =
        wrap(t.toExternalForm())

}
