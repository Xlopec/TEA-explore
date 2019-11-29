
import com.oliynick.max.elm.time.travel.gson.gson
import core.data.Id
import core.data.Name
import core.data.User
import core.data.photo
import io.kotlintest.shouldBe
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.immutableListOf
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import protocol.*
import java.util.*

private data class StringMapHolder(val values: Map<String?, Int?>)

private data class StringListHolder(val screens: ImmutableList<String>)

@RunWith(JUnit4::class)
class GsonAdapterTest {

    private val gson = gson()

    private val testUser = User(
        Id(UUID.randomUUID()),
        Name("John"),
        listOf(
            photo("https://www.google.com"),
            photo("https://www.google.com1"),
            photo("https://www.google.com2")
        )
    )

    private val testConverters = converters {
        +URLConverter
        +UUIDConverter
    }

    @Test
    fun `test initial value is same as parsed one`() {

        val initialValue = SomeTestCommand(SomeTestString("hello"), listOf(1234))
            .toValue(testConverters)
        val json = gson.toJson(initialValue)
        val fromJsonValue = gson.fromJson(json, Value::class.java)

        initialValue shouldBe fromJsonValue
    }

    @Test
    fun `test initial map is same as parsed one`() {

        val initialValue = StringMapHolder(mapOf("a" to 123, "b" to 1, null to null, null to 1, "" to null))
            .toValue(testConverters)
        val json = gson.toJson(initialValue)
        val fromJsonValue = gson.fromJson(json, Value::class.java)

        initialValue shouldBe fromJsonValue
    }

    @Test
    fun `test initial value with custom field type is same as parsed one`() {

        val initialValue = StringListHolder(immutableListOf("A", "B", "C"))
            .toValue(testConverters)
        val json = gson.toJson(initialValue)
        val fromJsonValue = gson.fromJson(json, Value::class.java)

        initialValue shouldBe fromJsonValue
    }

    @Test
    fun `test initial value with custom null field type is same as parsed one`() {

        val initialValue = testUser.toValue(testConverters)
        val json = gson.toJson(initialValue)
        val fromJsonValue = gson.fromJson(json, Value::class.java)

        initialValue shouldBe fromJsonValue
    }

    @Test
    fun `test initial command 'ApplyMessage' is same as parsed one`() {

        val initialValue = testUser.toValue(testConverters)
        val initialApplyCmd = ApplyMessage(initialValue)

        val cmdJson = gson.toJson(initialApplyCmd)
        val fromJson = gson.fromJson(cmdJson, ApplyMessage::class.java)

        initialApplyCmd shouldBe fromJson
    }

    @Test
    fun `test initial command 'ApplyState' is same as parsed one`() {

        val initialValue = testUser.toValue(testConverters)
        val initialApplyCmd = ApplyState(initialValue)

        val applyMessageJson = gson.toJson(initialApplyCmd)
        val fromJson = gson.fromJson(applyMessageJson, ApplyState::class.java)

        initialApplyCmd shouldBe fromJson
    }

    @Test
    fun `test initial command 'NotifyComponentSnapshot' is same as parsed one`() {

        val initialValue = testUser.toValue(testConverters)
        val initialApplyCmd = NotifyComponentSnapshot(initialValue, initialValue, initialValue)

        val applyMessageJson = gson.toJson(initialApplyCmd)
        val fromJson = gson.fromJson(applyMessageJson, NotifyComponentSnapshot::class.java)

        initialApplyCmd shouldBe fromJson
    }

    @Test
    fun `test initial message 'NotifyComponentAttached' is same as parsed one`() {

        val initialValue = testUser.toValue(testConverters)
        val initialApplyCmd = NotifyComponentAttached(initialValue)

        val applyMessageJson = gson.toJson(initialApplyCmd)
        val fromJson = gson.fromJson(applyMessageJson, NotifyComponentAttached::class.java)

        initialApplyCmd shouldBe fromJson
    }

    @Test
    fun `test initial message 'ActionApplied' is same as parsed one`() {

        val initialApplyCmd = ActionApplied(UUID.randomUUID())
        val applyMessageJson = gson.toJson(initialApplyCmd)
        val fromJson = gson.fromJson(applyMessageJson, ActionApplied::class.java)

        initialApplyCmd shouldBe fromJson
    }

}