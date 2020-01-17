package com.oliynick.max.elm.core.component

import com.oliynick.max.elm.core.loop.zalop.Component
import core.misc.throwingResolver
import core.scope.runBlockingInNewScope
import io.kotlintest.matchers.asClue
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.shouldBe
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.setMain
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.Executors

@RunWith(JUnit4::class)
class LooperTest {

    @Test
    fun `test component emits a correct sequence of snapshots`() = runBlockingInNewScope {

        val env = Env<String, String, String>(
            "",
            { c -> setOf(c) },
            { m, _ -> m.noCommand() }
        )

        val component = Component(env)
        val messages = arrayOf("a", "b", "c")
        val snapshots =
            component(*messages).take(messages.size + 1).toList(ArrayList(messages.size + 1))

        snapshots shouldBe listOf(
            Initial("", emptySet()),
            Regular("a", "a", emptySet()),
            Regular("b", "b", emptySet()),
            Regular("c", "c", emptySet())
        )
    }

    @Test
    fun `test component emits a correct sequence of snapshots if initial commands were present`() =
        runBlockingInNewScope {

            val env = Env<String, String, String>(
                InitializerLegacy("", "a", "b", "c"),
                { c -> setOf(c) },
                { m, _ -> m.noCommand() }
            )

            val component = Component(env)
            val messages = arrayOf("d", "f", "g")
            val snapshots =
                component(*messages).take(messages.size + 1).toList(ArrayList(messages.size + 1))

            snapshots shouldBe listOf(
                Initial("", setOf("a", "b", "c")),
                Regular("a", "a", emptySet()),
                Regular("b", "b", emptySet()),
                Regular("c", "c", emptySet())
            )
        }

    @Test
    fun `test component emits a correct sequence of snapshots if we have recursive calculations`() =
        runBlockingInNewScope {

            val env = Env<Char, Char, String>(
                "",
                { ch ->
                    if (ch == 'a') setOf(
                        ch.inc(),// only this message should be consumed
                        ch.inc().inc(),
                        ch.inc().inc().inc()
                    ) else emptySet()
                },
                { m, str -> (str + m).command(m) }
            )

            val component = Component(env)
            val snapshots = component('a').take(3).toCollection(ArrayList())

            @Suppress("RemoveExplicitTypeArguments")// helps to track down types when refactoring
            snapshots shouldBe listOf<Snapshot<Char, String, Char>>(
                Initial("", emptySet()),
                Regular('a', "a", setOf('a')),
                Regular('b', "ab", setOf('b'))
            )
        }

    @Test
    fun `test component emits a correct sequence of snapshots if update returns set of messages`() =
        runBlockingInNewScope {

            val env = Env<Char, Char, Pair<String, Boolean>>(
                "" to true,
                { ch -> setOf(ch) },
                { m, (str, flag) ->
                    (str + m to false).command(
                        if (flag) setOf(
                            'b',
                            'c'
                        ) else emptySet()
                    )
                }
            )

            val component = Component(env)
            val snapshots = component('a').take(3).toCollection(ArrayList())

            @Suppress("RemoveExplicitTypeArguments")// helps to track down types when refactoring
            snapshots shouldBe listOf<Snapshot<Char, Pair<String, Boolean>, Char>>(
                Initial("" to true, emptySet()),
                Regular('a', "a" to false, setOf('b', 'c')),
                Regular('b', "ab" to false, emptySet())
            )
        }

    @Test
    fun `test interceptor sees an original sequence of snapshots`() = runBlockingInNewScope {

        val env = Env<String, String, String>(
            "",
            { c -> setOf(c) },
            { m, _ -> m.noCommand() }
        )

        val sink = mutableListOf<Snapshot<String, String, String>>()
        val component = Component(env) with { sink.add(it) }
        val messages = arrayOf("a", "b", "c")
        val snapshots =
            component(*messages).take(messages.size + 1).toList(ArrayList(messages.size + 1))

        sink shouldBe snapshots
    }

    @Test
    fun `test component's snapshots shared among consumers`() = runBlockingInNewScope {

        val env = Env<Char, Char, String>(
            "",
            { ch ->
                if (ch == 'a') setOf(
                    ch.inc(),// only this message should be consumed
                    ch.inc().inc(),
                    ch.inc().inc().inc()
                ) else emptySet()
            },
            { m, str -> (str + m).command(m) }
        )

        val take = 3
        val component = Component(env)
        val snapshots2Deferred =
            async {
                component(emptyFlow()).onEach { println("2 $it") }.take(take)
                    .toCollection(ArrayList())
            }
        val snapshots1Deferred = async {
            component('a').onEach { println("1 $it") }.take(take).toCollection(ArrayList())
        }

        @Suppress("RemoveExplicitTypeArguments")// helps to track down types when refactoring
        val expected = listOf<Snapshot<Char, String, Char>>(
            Initial("", emptySet()),
            Regular('a', "a", setOf('a')),
            Regular('b', "ab", setOf('b'))
        )

        snapshots1Deferred.await().asClue { it shouldBe expected }
        snapshots2Deferred.await().asClue { it shouldBe expected }
    }

    @Test
    fun `test component gets initialized only once if we have multiple consumers`() =
        runBlockingInNewScope {

            val countingInitializer = object {

                val invocations = atomic(0)

                fun initializer(): InitializerLegacy<String, Nothing> = {
                    invocations.incrementAndGet()
                    yield()
                    "bar" to emptySet()
                }
            }

            val env = Env<Char, Char, String>(
                countingInitializer.initializer(),
                ::throwingResolver,
                { _, s -> s.noCommand() }
            )

            val component = Component(env)

            countingInitializer.invocations.value shouldBe 0

            val coroutines = 100
            val jobs = (0 until coroutines).map { launch { component('a').first() } }
                .toCollection(ArrayList(coroutines))

            jobs.joinAll()

            countingInitializer.invocations.value shouldBe 1
        }

    @Test
    fun `test component's job gets canceled properly`() = runBlockingInNewScope {

        val env = Env(
            "",
            ::foreverWaitingResolver,
            { m, _ -> m.command(m) }
        )

        val component = Component(env)
        val job = launch { component("a", "b", "c").toList(ArrayList()) }

        yield()
        job.cancel()

        job.isActive.shouldBeFalse()
        isActive.shouldBeTrue()
    }

    companion object {

        val mainThreadSurrogate = Executors.newSingleThreadExecutor().asCoroutineDispatcher().also {
            Dispatchers.setMain(it)
        }

        @BeforeClass
        @JvmStatic
        fun setup() {

            // things to execute once and keep around for the class
        }

        @AfterClass
        @JvmStatic
        fun teardown() {
            // Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
            //mainThreadSurrogate.close()
        }
    }

    /*@After
    fun tearDown() {
        Dispatchers.resetMain() // reset main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }*/

    @Test
    fun `test component doesn't block if serves multiple message sources`() =
        runBlockingInNewScope(Dispatchers.Main + Job()) {

            val env = Env<Char, Char, String>(
                "",
                ::throwingResolver,
                { m, _ -> m.toString().noCommand() }
            )

            val range = 'a'..'h'
            val component = Component(env)

            val chan1 = Channel<Char>()
            val chan2 = Channel<Char>()

            val snapshots2Deferred = async {
                component(chan2.consumeAsFlow().delayEach(50)).onEach { println("1 $it") }
                    .take(1 + range.count())
                    .toCollection(ArrayList())
            }

            val snapshots1Deferred = async {
                component(chan1.consumeAsFlow().delayEach(50)).onEach { println("2 $it") }
                    .take(1 + range.count())
                    .toCollection(ArrayList())
            }

            range.forEachIndexed { index, ch ->
                if (index % 2 == 0) {
                    chan1.send(ch)
                    println("offer1 $ch")
                } else {
                    chan2.send(ch)
                    println("offer2 $ch")
                }

            }

            val head = listOf(Initial("", emptySet<Char>()))

            val expected1: List<Snapshot<Char, String, Char>> =
                head + range/*.filterIndexed { index, _ -> index % 2 == 0 }*/.map { ch ->
                    Regular(
                        ch,
                        ch.toString(),
                        emptySet()
                    )
                }

            snapshots1Deferred.await().asClue { it shouldContainExactly expected1 }
            snapshots2Deferred.await().asClue { it shouldContainExactly expected1 }
        }

}

private suspend fun foreverWaitingResolver(
    m: String
): Set<String> {

    delay(Long.MAX_VALUE)

    error("Improper cancellation, message=$m")
}