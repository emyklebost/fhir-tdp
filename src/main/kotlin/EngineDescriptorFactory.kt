package no.nav.helse

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.json.JsonParser
import com.sksamuel.hoplite.yaml.YamlParser
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FilePosition
import org.junit.platform.engine.support.descriptor.FileSource
import java.nio.file.Path
import kotlin.io.path.forEachLine
import kotlin.io.path.name

object EngineDescriptorFactory {
    fun create(engineId: UniqueId, specFiles: List<Path>): EngineDescriptor {
        val engineDesc = EngineDescriptor(engineId, "FHIR Validator")

        specFiles.forEachIndexed { tsIndex, path ->
            val spec = configLoader.loadConfigOrThrow<Specification>(path)
            val validator = FhirValidator.create(spec.validator, path)

            val testSuiteId = engineId.append<TestSuiteDescriptor>("$tsIndex")
            val testSuiteDesc = TestSuiteDescriptor(testSuiteId, spec.name ?: path.name, FileSource.from(path.toFile()))

            spec.tests.forEachIndexed { tcIndex, testCase ->
                val testCaseId = testSuiteId.append<TestCaseDescriptor>("$tcIndex")
                val testCaseDesc = TestCaseDescriptor(testCaseId, testCase, validator, path.fileSource(tcIndex))
                testSuiteDesc.addChild(testCaseDesc)
            }

            engineDesc.addChild(testSuiteDesc)
        }

        return engineDesc
    }
}

private inline fun <reified T> UniqueId.append(value: String) = append(T::class.simpleName, value)!!

/** Parsers needs to be explicitly mapped to file-extensions to work with ShadowJar. */
private val configLoader = ConfigLoader.Builder()
    .addFileExtensionMapping("json", JsonParser())
    .addFileExtensionMapping("yaml", YamlParser())
    .addFileExtensionMapping("yml", YamlParser())
    .build()

/** Creates a FileSource with FilePosition (line, column) of the 'source' property
 ** of the Test at [[testIndex]]. Works with both json and yaml files. */
private fun Path.fileSource(testIndex: Int): FileSource {
    val pattern = if (name.endsWith(".json")) "\"source\"" else "[ {]source: "
    val filePosition = findAllMatches(Regex(pattern)).elementAtOrNull(testIndex)
    return FileSource.from(toFile(), filePosition)
}

private fun Path.findAllMatches(pattern: Regex) =
    sequence<FilePosition> {
        val commentLinePattern = Regex("^ *#")
        var lineNr = 1
        forEachLine { line ->
            if (!commentLinePattern.matches(line)) {
                pattern.findAll(line).forEach {
                    yield(FilePosition.from(lineNr, it.range.first + 1))
                }
            }
            lineNr++
        }
    }
