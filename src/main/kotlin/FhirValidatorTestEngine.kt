package no.nav.helse

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.json.JsonParser
import com.sksamuel.hoplite.parsers.PropsParser
import com.sksamuel.hoplite.yaml.YamlParser
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DirectorySelector
import org.junit.platform.engine.discovery.FileSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FilePosition
import org.junit.platform.engine.support.descriptor.FileSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.streams.asSequence

class FhirValidatorTestEngine : TestEngine {
    override fun getId() = "fhir-validator"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val specFiles = discoveryRequest.run {
            val fileExt = listOf("test.json", "test.yml", "test.yaml", "test.properties")

            val files = getSelectorsByType(DirectorySelector::class.java)
                .map { it.path }
                .flatMap { Files.walk(it).asSequence() }
                .filter { fileExt.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }

            files + getSelectorsByType(FileSelector::class.java).map { it.path }
        }

        return createRootTestDescriptor(uniqueId, specFiles)
    }

    override fun execute(request: ExecutionRequest) {
        val rootTestDesc = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.scope(rootTestDesc) {
            rootTestDesc.children
                .mapNotNull { it as? TestSuiteDescriptor }
                .forEach { it.execute(listener) }
        }
    }
}

private fun createRootTestDescriptor(engineId: UniqueId, specFiles: List<Path>): TestDescriptor {
    val rootTestDesc = EngineDescriptor(engineId, "FHIR Validator")

    specFiles.forEachIndexed { tsIndex, path ->
        val spec = configLoader.loadConfigOrThrow<Specification>(path)
        val validator = ValidatorFactory.create(spec.validator, path)

        val testSuiteId = engineId.append<TestSuiteDescriptor>("$tsIndex")
        val testSuiteDesc = TestSuiteDescriptor(testSuiteId, spec.name ?: path.name, FileSource.from(path.toFile()))

        spec.tests.forEachIndexed { tcIndex, testCase ->
            val testCaseId = testSuiteId.append<TestCaseDescriptor>("$tcIndex")
            val testCaseDesc = TestCaseDescriptor(testCaseId, testCase, validator, path.fileSource(testCase))
            testSuiteDesc.addChild(testCaseDesc)
        }

        rootTestDesc.addChild(testSuiteDesc)
    }

    return rootTestDesc
}

private class TestSuiteDescriptor(id: UniqueId, name: String, source: FileSource) :
    AbstractTestDescriptor(id, name, source) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            children
                .mapNotNull { it as? TestCaseDescriptor }
                .forEach { it.execute(listener) }
        }
}

// This can probably be done better using JsonPath or something.
// Only works with json files.
private fun Path.fileSource(testCase: Specification.TestCase): FileSource {
    if (name.endsWith(".json")) {
        readLines().forEachIndexed { line, str ->
            var column = str.indexOf(testCase.source)
            if (column != -1) {
                // BUG: If multiple tests uses the same source they will all get FilePosition of the first.
                column = str.substring(0, column).lastIndexOf("\"${Specification.TestCase::source.name}\"")
                return FileSource.from(toFile(), FilePosition.from(line + 1, column + 1))
            }
        }
    }

    return FileSource.from(toFile())
}

/** Parsers needs to be explicitly mapped to file-extensions to work with ShadowJar. */
private val configLoader = ConfigLoader.Builder()
    .addFileExtensionMapping("properties", PropsParser())
    .addFileExtensionMapping("json", JsonParser())
    .addFileExtensionMapping("yaml", YamlParser())
    .addFileExtensionMapping("yml", YamlParser())
    .build()
