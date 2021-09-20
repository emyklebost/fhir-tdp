package no.nav.helse

import com.sksamuel.hoplite.ConfigLoader
import org.junit.platform.engine.*
import org.junit.platform.engine.discovery.DirectorySelector
import org.junit.platform.engine.discovery.FileSelector
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import java.io.File

class FhirValidatorTestEngine : TestEngine {
    override fun getId() = "fhir"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val specFiles = discoveryRequest.run {
            val fileExt = listOf("test.json", "test.yml", "test.yaml", "test.properties")

            val files = getSelectorsByType(DirectorySelector::class.java)
                .map { it.directory }
                .ifEmpty { listOf(File("src/test/resources")) }
                .flatMap { it.walk() }
                .filter { fileExt.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }

            files + getSelectorsByType(FileSelector::class.java).map { it.file }
        }

        return createTestEngineDescriptor(uniqueId, specFiles)
    }

    override fun execute(request: ExecutionRequest) {
        val testRoot = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.scope(testRoot) {
            testRoot.children
                .mapNotNull { it as? TestSuiteDescriptor }
                .forEach { listener.scope(it) { it.execute(listener) } }
        }
    }
}

private fun createTestEngineDescriptor(engineId: UniqueId, specFiles: List<File>): EngineDescriptor {
    val testEngineDesc = EngineDescriptor(engineId, "FHIR Validator")

    specFiles.forEachIndexed { i0, file ->
        val testSuiteId = engineId.append<TestSuiteDescriptor>("$i0")
        val testSuiteDesc = TestSuiteDescriptor(testSuiteId, file.name)

        val spec = ConfigLoader().loadConfigOrThrow<Specification>(file)
        val validator = ValidatorFactory.create(spec.validator)

        spec.testCases.forEachIndexed { i1, testCase ->
            val testCaseId = testSuiteId.append<TestCaseDescriptor>("$i1")
            val testCaseDesc = TestCaseDescriptor(testCaseId, testCase, validator, FileSource.from(file))

            val noUnexpectedErrorsId = testCaseId.append<HasNoUnexpectedErrorsDescriptor>("0")
            testCaseDesc.addChild(HasNoUnexpectedErrorsDescriptor(noUnexpectedErrorsId, testCase))

            testCase.expectedIssues.forEachIndexed { i2, issue ->
                val expectedIssueId = testCaseId.append<HasExpectedIssueDescriptor>("$i2")
                testCaseDesc.addChild(HasExpectedIssueDescriptor(expectedIssueId, issue))
            }

            testSuiteDesc.addChild(testCaseDesc)
        }

        testEngineDesc.addChild(testSuiteDesc)
    }

    return testEngineDesc
}
