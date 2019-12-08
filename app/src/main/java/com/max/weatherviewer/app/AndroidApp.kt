// used by OS
@file:Suppress("unused")

package com.max.weatherviewer.app

import android.app.Activity
import android.app.Application
import com.max.weatherviewer.BuildConfig
import com.max.weatherviewer.CloseApp
import com.max.weatherviewer.adapters
import com.max.weatherviewer.env.Environment
import com.max.weatherviewer.retrofit
import com.oliynick.max.elm.core.component.Component
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

class AndroidApp : Application() {

    val environment by unsafeLazy {
        Environment(retrofit, AppComponentScope, BuildConfig.DEBUG)
    }

    val component by unsafeLazy { environment.appComponent() }

    val messages = Channel<Message>()
}

inline val Activity.androidApp: AndroidApp
    get() = application as AndroidApp

inline val Activity.appComponent: Component<Message, State>
    get() = androidApp.component

inline val Activity.appMessages: Channel<Message>
    get() = androidApp.messages

inline val Activity.environment: Environment
    get() = androidApp.environment

inline val Activity.closeAppCommands: Flow<CloseApp>
    get() = androidApp.environment.closeCommands.consumeAsFlow()

private val retrofit by unsafeLazy {
    retrofit {
        adapters.forEach { (cl, adapter) ->
            registerTypeAdapter(cl.java, adapter)
        }
    }
}

private fun <T> unsafeLazy(block: () -> T) = lazy(LazyThreadSafetyMode.NONE, block)

private object AppComponentScope : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        Executors.newSingleThreadExecutor { r -> Thread(r, "App Scheduler") }
            .asCoroutineDispatcher()
}