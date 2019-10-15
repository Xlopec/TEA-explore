package com.oliynick.max.elm.core.component

import com.oliynick.max.elm.time.travel.protocol.*
import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.client.features.websocket.ws
import io.ktor.http.HttpMethod
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.readBytes
import io.ktor.http.cio.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val httpClient by lazy { HttpClient { install(WebSockets) } }

data class Settings(val id: ComponentId, val host: String = "localhost", val port: UInt = 8080U) {
    init {
        require(host.isNotEmpty() && host.isNotBlank())
    }
}

fun <M : Any, C : Any, S : Any> CoroutineScope.component(settings: Settings,
                                                         initialState: S,
                                                         resolver: Resolver<C, M>,
                                                         update: Update<M, S, C>,
                                                         interceptor: Interceptor<M, S, C> = ::emptyInterceptor,
                                                         vararg initialCommands: C): Component<M, S> {

    @Suppress("RedundantSuspendModifier")
    suspend fun loader() = initialState to setOf(*initialCommands)

    return component(settings, ::loader, resolver, update, interceptor)
}

fun <M : Any, C : Any, S : Any> CoroutineScope.component(settings: Settings,
                                                         initializer: Initializer<S, C>,
                                                         resolver: Resolver<C, M>,
                                                         update: Update<M, S, C>,
                                                         interceptor: Interceptor<M, S, C> = ::emptyInterceptor): Component<M, S> {

    val (messages, states) = webSocketComponent(settings, initializer, resolver, update, interceptor)

    return newComponent(states, messages)
}

private fun <M : Any, C : Any, S : Any> CoroutineScope.webSocketComponent(settings: Settings,
                                                                          initializer: Initializer<S, C>,
                                                                          resolver: Resolver<C, M>,
                                                                          update: Update<M, S, C>,
                                                                          interceptor: Interceptor<M, S, C>): ComponentInternal<M, S> {

    val snapshots = Channel<NotifyComponentSnapshot>()
    val dependencies = Dependencies(initializer, resolver, update, spyingInterceptor<M, C, S>(snapshots).with(interceptor))
    val statesChannel = BroadcastChannel<S>(Channel.CONFLATED)
    val messages = Channel<M>()

    launch {
        httpClient.ws(HttpMethod.Get, settings.host, settings.port.toInt()) {
            // says 'hello' to a server; 'send' call will be suspended until the very first state gets computed
            launch { send(settings.id, NotifyComponentAttached(statesChannel.asFlow().first())) }

            var computationJob = launch { loop(initializer, dependencies, messages, statesChannel) }

            suspend fun applyMessage(message: ClientMessage) {
                @Suppress("UNCHECKED_CAST")
                when (message) {
                    is ApplyMessage -> messages.send(message.message as M)
                    is ApplyState -> {
                        // cancels previous computation job and starts a new one
                        computationJob.cancel()
                        computationJob = launch { loop({ message.state as S to emptySet() }, dependencies, messages, statesChannel) }
                    }
                }.safe
            }
            // observes changes and notifies the server
            launch { snapshots.consumeAsFlow().collect { snapshot -> send(settings.id, snapshot) } }
            // parses and applies incoming messages
            incoming.consumeAsFlow()
                .filterIsInstance<Frame.Binary>()
                .map { ReceivePacket.unpack(it.readBytes()) }
                .filterIsInstance<ClientMessage>()
                .collect { message -> applyMessage(message) }
        }
    }

    return messages to statesChannel.asFlow()
}

private suspend fun WebSocketSession.send(id: ComponentId, message: ServerMessage) = send(SendPacket.pack(id, message))

private fun <M : Any, C : Any, S : Any> spyingInterceptor(sink: SendChannel<NotifyComponentSnapshot>): Interceptor<M, S, C> {
    return { message, prevState, newState, _ -> sink.send(NotifyComponentSnapshot(message, prevState, newState)) }
}