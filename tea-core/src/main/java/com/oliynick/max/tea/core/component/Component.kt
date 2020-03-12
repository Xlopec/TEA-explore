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

@file:Suppress("unused", "MemberVisibilityCanBePrivate", "FunctionName")
@file:OptIn(UnstableApi::class)

package com.oliynick.max.tea.core.component

import com.oliynick.max.tea.core.Env
import com.oliynick.max.tea.core.EnvBuilder
import com.oliynick.max.tea.core.Initial
import com.oliynick.max.tea.core.Initializer
import com.oliynick.max.tea.core.Regular
import com.oliynick.max.tea.core.Snapshot
import com.oliynick.max.tea.core.UnstableApi
import com.oliynick.max.tea.core.component.internal.emitAll
import com.oliynick.max.tea.core.component.internal.finishWith
import com.oliynick.max.tea.core.component.internal.foldFlatten
import com.oliynick.max.tea.core.component.internal.into
import com.oliynick.max.tea.core.component.internal.parMapTo
import com.oliynick.max.tea.core.component.internal.shareConflated
import com.oliynick.max.tea.core.component.internal.startFrom
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias Component<M, S, C> = (messages: Flow<M>) -> Flow<Snapshot<M, S, C>>

/**
 * Alias for a pure function that accepts message with current state and returns the next state with possible empty set of commands
 * to feed [resolver][Resolver]
 * @param M incoming messages
 * @param S state of the component
 * @param C commands to be executed
 */
typealias Updater<M, S, C> = (message: M, state: S) -> UpdateWith<S, C>

/**
 * Alias for a function that resolves effects and returns messages to feed [update][Updater] function
 * @param M incoming messages
 * @param C commands to be executed
 */
typealias Resolver<C, M> = suspend (command: C) -> Set<M>

/**
 * Alias for result of the [update][Updater] function
 * @param S state of the component
 * @param C commands to be executed
 */
typealias UpdateWith<S, C> = Pair<S, Set<C>>

fun <M, C, S> Component(
    initializer: Initializer<S, C>,
    resolver: Resolver<C, M>,
    updater: Updater<M, S, C>,
    config: EnvBuilder<M, S, C>.() -> Unit = {}
): Component<M, S, C> = Component(Env(initializer, resolver, updater, config))

fun <M, S, C> Component(
    env: Env<M, S, C>
): Component<M, S, C> {

    val input = Channel<M>(Channel.RENDEZVOUS)
    val upstream = env.upstream(input.consumeAsFlow(), env.init()).shareConflated()

    return { messages -> upstream.downstream(messages, input) }
}

@UnstableApi
fun <M, S, C> Env<M, S, C>.upstream(
    messages: Flow<M>,
    snapshots: Flow<Initial<S, C>>
) = snapshots.flatMapConcat { startFrom -> compute(startFrom, messages) }

@UnstableApi
fun <M, S, C> Flow<Snapshot<M, S, C>>.downstream(
    input: Flow<M>,
    upstreamInput: SendChannel<M>
): Flow<Snapshot<M, S, C>> =
    channelFlow {
        @Suppress("NON_APPLICABLE_CALL_FOR_BUILDER_INFERENCE")
        onStart { launch { input.into(upstreamInput) } }
            .into(channel)
    }

@UnstableApi
fun <S, C> Env<*, S, C>.init(): Flow<Initial<S, C>> =
    flow {
        // FIXME: do not inline variable, Back-end (JVM) KtCallExpression will be thrown
        val initial = withContext(io) {
            initializer()
        }

        emit(initial)
    }

@UnstableApi
fun <M, S, C> Env<M, S, C>.compute(
    startFrom: Initial<S, C>,
    messages: Flow<M>
): Flow<Snapshot<M, S, C>> =
    resolveAsFlow(startFrom.commands).finishWith(messages)
        .foldFlatten<M, Snapshot<M, S, C>>(startFrom) { s, m -> computeNextSnapshot(s.currentState, m) }
        .startFrom(startFrom)

@UnstableApi
suspend fun <M, S, C> Env<M, S, C>.computeNextSnapshotsRecursively(
    state: S,
    messages: Iterator<M>
): Flow<Snapshot<M, S, C>> {

    val message = messages.nextOrNull() ?: return emptyFlow()

    val (nextState, commands) = update(message, state)

    return computeNextSnapshotsRecursively(nextState, resolve(commands).iterator())
        .startFrom(Regular(nextState, commands, state, message))
}

@UnstableApi
suspend fun <M, S, C> Env<M, S, C>.computeNextSnapshot(
    state: S,
    message: M
): Flow<Snapshot<M, S, C>> {
    // todo: we need to add possibility to return own versions
    //  of snapshots, e.g. user might be interested only in current
    //  version of state
    val (nextState, commands) = update(message, state)

    return computeNextSnapshotsRecursively(nextState, resolve(commands).iterator())
        .startFrom(Regular(nextState, commands, state, message))
}

private fun <M, S, C> Env<M, S, C>.resolveAsFlow(
    commands: Collection<C>
): Flow<M> =
    flow { emitAll(resolve(commands)) }

private suspend fun <M, S, C> Env<M, S, C>.update(
    message: M,
    state: S
): UpdateWith<S, C> =
    withContext(computation) { updater(message, state) }

private suspend fun <M, C> Env<M, *, C>.resolve(
    commands: Collection<C>
): Iterable<M> =
    commands
        .parMapTo(dispatcher = io) { cmd -> resolver(cmd) }
        .flatten()

private fun <E> Iterator<E>.nextOrNull() = if (hasNext()) next() else null
