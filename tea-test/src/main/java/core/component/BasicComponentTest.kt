@file:Suppress("FunctionName")

package core.component

import com.oliynick.max.tea.core.*
import com.oliynick.max.tea.core.component.*
import core.misc.messageAsCommand
import core.misc.throwingResolver
import core.scope.coroutineDispatcher
import core.scope.runBlockingInTestScope
import io.kotlintest.matchers.asClue
import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.matchers.collections.shouldContainExactly
import io.kotlintest.matchers.throwable.shouldHaveMessage
import io.kotlintest.matchers.types.shouldBeSameInstanceAs
import io.kotlintest.matchers.withClue
import io.kotlintest.shouldBe
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Ignore
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.Executors
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

abstract class BasicComponentTest(
    protected val factory: CoroutineScope.(Env<Char, String, Char>) -> Component<Char, String, Char>,
) {

    private companion object {
        const val TestTimeoutMillis = 5_000L
        const val ThreadName = "test thread"

        val CoroutineDispatcher =
            Executors.newSingleThreadExecutor { r -> Thread(r, ThreadName) }
                .asCoroutineDispatcher()
    }

    @Test(timeout = TestTimeoutMillis/*might get stuck in case of regression*/)
    @Ignore
    fun `test when component's scope is canceled, then delayed resolvers are canceled as well`() =
        runBlockingInTestScope {

            fun testInput(
                input: Initial<String, Char>,
            ) = input.commands.asFlow().delayEach(10L)

            val env = Env<Char, String, Char>(
                Initializer(""),
                ::foreverWaitingResolver,
                ::messageAsCommand,
                coroutineDispatcher,
                coroutineDispatcher,
           //     CoroutineScope(coroutineDispatcher)
            )

          //  try {
                val l = env.upstream(
                    flowOf(Initial("", setOf('a', 'b', 'c', 'd', 'e', 'f'))),
                    ::noOpSink,
                    ::testInput
                )
                    .collect { snapshot ->

                    println("each $snapshot")

                    if ('a' in snapshot.commands && snapshot is Regular) {
                        // 'b' is the last snapshot in current emission
                        // because it's the last element in the input flow
                        // and because after this input flow stops, so no new
                        // regular snapshots will be computed
                        println("canceling $snapshot")
                        //env.scope.cancel()//coroutineContext[Job.Key]!!.cancelChildren()
                    }
                }
           /* } catch (th: Throwable) {
                println("catch $th")
            }*/

          //  env.scope.isActive.shouldBeFalse()
        }

    @Test//(timeout = 100L)
    fun `test shit`() = runBlockingInTestScope {

        val env = Env<Char, String, Char>(
            Initializer(""),
            ::foreverWaitingResolver,
            { m, str -> (str + m).command(m) },
            coroutineDispatcher,
            coroutineDispatcher
        )

        val c = factory(env)

        val j1 = launch(env.io) {
            c('a'..'f')
                .take(1 + ('g' - 'a'))
                .collect {
                //println("some1 $it")
            }
        }

        //delay(100L)
        //delay(100L)
        j1.join()
        //delay(100L)
        yield()
        //j1.cancel()

        println("start")

       // launch(env.io) {
            c.invoke('g'..'k')
                .take(1 + ('k' - 'g'))
                .collect {
                   // println("some2 $it")
                }
        //}.join()
    }

    @Test(timeout = TestTimeoutMillis/*might get stuck in case of regression*/)
    fun `test when receiving new Initial snapshot previous computations get canceled`() =
        runBlockingInTestScope {

            val env = Env<Char, String, Char>(
                Initializer(""),
                ::noOpResolver,
                ::messageAsCommand,
                coroutineDispatcher,
                coroutineDispatcher
            )

            val initialStates = listOf(
                Initial("", setOf('c')),
                Initial("a", setOf('d')),
                Initial("b", setOf('e'))
            )

            fun testInput(
                input: Initial<String, Char>,
            ) = input.commands.asFlow()
                .onStart {
                    if (input !== initialStates.last()) {
                        delay(Long.MAX_VALUE)
                    }
                }

            val l = env.upstream(
                initialStates.asFlow(),
                ::noOpSink,
                ::testInput
            ).toCollection(ArrayList())
            /* } catch (th: Throwable) {
                 println("catch $th")
             }*/

            println(l.joinToString(separator = "\n"))

            
        }

    @Test
    fun `test component emits a correct sequence of snapshots`() = runBlocking {

        val env = Env<Char, String, Char>(
            "",
            ::throwingResolver,
            { m, _ -> m.toString().noCommand() }
        )

        val component = factory(env)
        val messages = arrayOf('a', 'b', 'c')
        val snapshots =
            component(*messages).take(messages.size + 1).toList(ArrayList(messages.size + 1))

        snapshots.shouldContainExactly(
            Initial("", emptySet()),
            Regular("a", emptySet(), "", 'a'),
            Regular("b", emptySet(), "a", 'b'),
            Regular("c", emptySet(), "b", 'c')
        )

        
    }

    @Test
    fun `test component emits a correct sequence of snapshots if we have recursive calculations`() =
        runBlocking {

            val env = Env<Char, String, Char>(
                "",
                { ch ->
                    // only message 'b' should be consumed
                    if (ch == 'a') ('b'..'d').toSet() else emptySet()
                },
                { m, str -> (str + m).command(m) }
            )

            val snapshots = factory(env)('a').take(3).toCollection(ArrayList())

            @Suppress("RemoveExplicitTypeArguments")// helps to track down types when refactoring
            snapshots shouldBe listOf<Snapshot<Char, String, Char>>(
                Initial("", emptySet()),
                Regular("a", setOf('a'), "", 'a'),
                Regular("ab", setOf('b'), "a", 'b')
            )
        }

    @Test
    fun `test interceptor sees an original sequence of snapshots`() = runBlocking {

        val env = Env<Char, String, Char>(
            "",
            { c -> setOf(c) },
            { m, _ -> m.toString().noCommand() }
        )

        val sink = mutableListOf<Snapshot<Char, String, Char>>()
        val component = factory(env) with { sink.add(it) }
        val messages = arrayOf('a', 'b', 'c')
        val snapshots =
            component(*messages).take(messages.size + 1).toList(ArrayList(messages.size + 1))

        sink shouldContainExactly snapshots
        
    }

    @Test
    fun `test component's snapshots shared among consumers`() = runBlocking {

        val env = Env<Char, String, Char>(
            "",
            { ch ->
                if (ch == 'a') setOf(
                    ch + 1,// only this message should be consumed
                    ch + 2,
                    ch + 3
                ) else emptySet()
            },
            { m, str -> (str + m).command(m) }
        )

        val take = 3
        val component = factory(env)
        val snapshots2Deferred =
            async {
                component(emptyFlow()).take(take)
                    .toCollection(ArrayList())
            }
        val snapshots1Deferred = async {
            component('a').take(take).toCollection(ArrayList())
        }

        @Suppress("RemoveExplicitTypeArguments")// helps to track down types when refactoring
        val expected = listOf<Snapshot<Char, String, Char>>(
            Initial("", emptySet()),
            Regular("a", setOf('a'), "", 'a'),
            Regular("ab", setOf('b'), "a", 'b')
        )

        snapshots1Deferred.await().asClue { it shouldContainExactly expected }
        snapshots2Deferred.await().asClue { it shouldContainExactly expected }
    }

    @Test
    fun `test component gets initialized only once if we have multiple consumers`() =
        runBlocking {

            val countingInitializer = object {

                val invocations = atomic(0)

                fun initializer(): Initializer<String, Nothing> = {
                    invocations.incrementAndGet()
                    yield()
                    Initial("bar", emptySet())
                }
            }

            val env = Env<Char, String, Char>(
                countingInitializer.initializer(),
                ::throwingResolver,
                { _, s -> s.noCommand() }
            )

            val component = factory(env)

            countingInitializer.invocations.value shouldBe 0

            val coroutines = 100
            val jobs = (0 until coroutines).map { launch { component('a').first() } }
                .toCollection(ArrayList(coroutines))

            jobs.joinAll()

            countingInitializer.invocations.value shouldBe 1
        }

    @Test
    fun `test component's job gets canceled properly`() = runBlocking {

        val env = Env<Char, String, Char>(
            "",
            ::foreverWaitingResolver,
            ::messageAsCommand
        )

        val component = factory(env)
        val job = launch { component('a', 'b', 'c').toList(ArrayList()) }

        yield()
        job.cancel()

        job.isActive.shouldBeFalse()
        isActive.shouldBeTrue()
        
    }

    @Test
    fun `test component doesn't block if serves multiple message sources`() =
        runBlockingInTestScope {

            val env = Env<Char, String, Char>(
                "",
                ::throwingResolver,
                { m, _ -> m.toString().noCommand() }
            )

            val range = 'a'..'h'
            val component = factory(env)

            val chan1 = Channel<Char>()
            val chan2 = Channel<Char>()

            val snapshots2Deferred = async {
                component(chan2.consumeAsFlow())
                    .take(1 + range.count())
                    .toCollection(ArrayList())
            }

            val snapshots1Deferred = async {
                component(chan1.consumeAsFlow())
                    .take(1 + range.count())
                    .toCollection(ArrayList())
            }

            range.forEachIndexed { index, ch ->
                if (index % 2 == 0) {
                    chan1.send(ch)
                } else {
                    chan2.send(ch)
                }

            }

            val expected: List<Snapshot<Char, String, Char>> =
                listOf(
                    Initial(
                        "",
                        emptySet<Char>()
                    )
                ) + range.mapIndexed { index, ch ->
                    Regular(
                        ch.toString(),
                        emptySet(),
                        if (index == 0) "" else ch.dec().toString(),
                        ch
                    )
                }

            snapshots1Deferred.await().asClue { it shouldContainExactly expected }
            snapshots2Deferred.await().asClue { it shouldContainExactly expected }
            
        }

    @Test
    fun `test resolver runs on a given dispatcher`() = runBlockingInTestScope {

        val env = Env<Char, String, Char>(
            Initializer(""),
            CheckingResolver(coroutineDispatcher),
            ::messageAsCommand,
            coroutineDispatcher
        )

        factory(env)('a'..'d')
            .take('d' - 'a').collect()

        
    }

    @Test
    // fixme later
    fun `test component throws exception given resolver throws exception`() =
        runBlockingInTestScope {

            val errorMessage = "error from resolver"

            val env = Env<Char, String, Char>(
                Initializer(""),
                ThrowingResolver(errorMessage),
                ::messageAsCommand,
                coroutineDispatcher
            )

            assertThrows<Throwable> {
                runBlocking {
                    factory(env)('a'..'d').collect()
                }
            }.shouldHaveMessage(errorMessage)
        }

    @Test
    fun `test initializer runs on a given dispatcher`() = runBlockingInTestScope {

        val env = Env<Char, String, Char>(
            CheckingInitializer(CoroutineDispatcher),
            ::throwingResolver,
            { m, _ -> m.toString().noCommand() },
            CoroutineDispatcher
        )

        factory(env)('a'..'d').take('d' - 'a').collect()

        
    }

    @Test
    fun `test updater runs on a given dispatcher`() = runBlockingInTestScope {

        val env = Env<Char, String, Char>(
            Initializer(""),
            ::throwingResolver,
            CheckingUpdater(Regex("$ThreadName @coroutine#\\d+")),
            computation = CoroutineDispatcher
        )

        factory(env)('a'..'d').take('d' - 'a').collect()

        
    }

}

@Suppress("UNUSED_PARAMETER")
private fun <T> noOpSink(t: T) = Unit

private fun CheckingInitializer(
    expectedDispatcher: CoroutineDispatcher,
): Initializer<String, Nothing> = {
    coroutineContext[ContinuationInterceptor] shouldBeSameInstanceAs expectedDispatcher
    Initial("", emptySet())
}

private fun CheckingResolver(
    expectedDispatcher: CoroutineDispatcher,
): Resolver<Any?, Nothing> = {
    coroutineContext[CoroutineDispatcher.Key] shouldBeSameInstanceAs expectedDispatcher
    emptySet()
}

private fun ThrowingResolver(
    message: String,
): Resolver<Any?, Nothing> =
    { error(message) }

private fun currentThreadName(): String =
    Thread.currentThread().name

private fun <M, S> CheckingUpdater(
    expectedThreadGroup: Regex,
): Updater<M, S, Nothing> = { _, s ->

    val threadName = currentThreadName()

    withClue("Thread name should match '${expectedThreadGroup.pattern}' but was '$threadName'") {
        threadName.matches(expectedThreadGroup).shouldBeTrue()
    }
    s.noCommand()
}

private suspend fun <T> noOpResolver(
    m: T,
): Set<Nothing> = emptySet()

private suspend fun <T> foreverWaitingResolver(
    m: T,
): Nothing {

    try {
        delay(Long.MAX_VALUE)
    } finally {
        println("cancelling for message $m")
        check(!coroutineContext.isActive) { "wrong state $coroutineContext" }
    }

    error("Improper cancellation, message=$m")
}

