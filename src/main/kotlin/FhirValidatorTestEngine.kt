package no.nav.helse

import com.sksamuel.hoplite.ConfigLoader
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
import org.junit.platform.engine.support.descriptor.FileSource
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
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

        return createRootTestDescriptor(uniqueId, specFiles.map { it })
    }

    override fun execute(request: ExecutionRequest) {
        val rootTestDesc = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.scope(rootTestDesc) {
            rootTestDesc.children
                .mapNotNull { it as? TestSuiteDescriptor }
                .forEach { listener.scope(it) { it.execute(listener) } }
        }
    }
}

private fun createRootTestDescriptor(engineId: UniqueId, specFiles: List<Path>): TestDescriptor {
    val rootTestDesc = EngineDescriptor(engineId, "FHIR Validator")

    specFiles.forEachIndexed { i0, path ->
        val testSuiteId = engineId.append<TestSuiteDescriptor>("$i0")
        val testSuiteDesc = TestSuiteDescriptor(testSuiteId, path.name)

        val spec = ConfigLoader().loadConfigOrThrow<Specification>(path)
        val validator = ValidatorFactory.create(spec.validator, path)

        spec.testCases.forEachIndexed { i1, testCase ->
            val testCaseId = testSuiteId.append<TestCaseDescriptor>("$i1")
            val testCaseDesc = TestCaseDescriptor(testCaseId, testCase, validator, FileSource.from(path.toFile()))

            val noUnexpectedErrorsId = testCaseId.append<HasNoUnexpectedErrorsDescriptor>("0")
            testCaseDesc.addChild(HasNoUnexpectedErrorsDescriptor(noUnexpectedErrorsId, testCase))

            testCase.expectedIssues.forEachIndexed { i2, issue ->
                val expectedIssueId = testCaseId.append<HasExpectedIssueDescriptor>("$i2")
                testCaseDesc.addChild(HasExpectedIssueDescriptor(expectedIssueId, issue))
            }

            testSuiteDesc.addChild(testCaseDesc)
        }

        rootTestDesc.addChild(testSuiteDesc)
    }

    return rootTestDesc
}

private class TestSuiteDescriptor(id: UniqueId, name: String) : AbstractTestDescriptor(id, name) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            children
                .mapNotNull { it as? TestCaseDescriptor }
                .forEach { listener.scope(it) { it.execute(listener) } }
        }
}
