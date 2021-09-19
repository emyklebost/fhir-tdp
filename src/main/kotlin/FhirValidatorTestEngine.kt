package no.nav.helse

import com.sksamuel.hoplite.ConfigLoader
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DirectorySelector
import org.junit.platform.engine.discovery.FileSelector
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import java.io.File
import java.lang.AssertionError

class FhirValidatorTestEngine : TestEngine {
    override fun getId() = "fhir"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val testRoot = EngineDescriptor(uniqueId, "FHIR Validator Tests")

        val specFiles = mutableListOf<File>()

        discoveryRequest.apply {
            val fileExt = listOf("test.json", "test.yml", "test.yaml", "test.properties")

            val files = getSelectorsByType(DirectorySelector::class.java)
                .map { it.directory }
                .ifEmpty { listOf(File("src/test/resources")) }
                .flatMap { it.walk() }
                .filter { fileExt.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }

            specFiles.addAll(files)
            specFiles.addAll(getSelectorsByType(FileSelector::class.java).map { it.file })
        }

        specFiles.forEach {
            val spec = ConfigLoader().loadConfigOrThrow<Specification>(it)
            val testSuite = TestSuiteDescriptor(testRoot.uniqueId, it.name, spec)
            testRoot.addChild(testSuite)
        }

        return testRoot
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

private class TestSuiteDescriptor(parentId: UniqueId, name: String, private val spec: Specification)
    : AbstractTestDescriptor(parentId.append<TestSuiteDescriptor>(name), name) {
    init {
        spec.testCases.forEach {
            addChild(TestCaseDescriptor(uniqueId, it.resource, it))
        }
    }

    override fun getType() = TestDescriptor.Type.CONTAINER

    fun execute(listener: EngineExecutionListener) {
        val validator = ValidatorFactory.create(spec.validator)
        val testRunner = ProfileValidationTest(validator)

        children
            .mapNotNull { it as? TestCaseDescriptor }
            .forEach { listener.scope(it) { it.execute(testRunner, listener) } }
    }
}

private class TestCaseDescriptor(parentId: UniqueId, name: String, private val testCase: Specification.TestCase)
    : AbstractTestDescriptor(parentId.append<TestCaseDescriptor>(name), name) {
    init {
        testCase.expectedIssues.forEachIndexed { i, it ->
            addChild(ExpectedIssueCheckDescriptor(uniqueId, i, it))
        }

        addChild(NoUnexpectedErrorsCheckDescriptor(uniqueId))
    }

    override fun getType() = TestDescriptor.Type.CONTAINER

    fun execute(testRunner: ProfileValidationTest, listener: EngineExecutionListener) {
        val result = testRunner.run(testCase)

        children
            .mapNotNull { it as? ExpectedIssueCheckDescriptor }
            .forEach { listener.scope(it) { it.execute(result) } }

        children
            .mapNotNull { it as? NoUnexpectedErrorsCheckDescriptor }
            .forEach { listener.scope(it) { it.execute(result) } }
    }
}

private class ExpectedIssueCheckDescriptor(parentId: UniqueId, index: Int, private val issue: Specification.Issue)
    : AbstractTestDescriptor(
        parentId.append<ExpectedIssueCheckDescriptor>("$index"),
        "Should have issue with severity=${issue.severity}") {
    override fun getType() = TestDescriptor.Type.TEST

    fun execute(result: TestResult) {
        println("Check if validation result contains $issue.")
        if (result.missingIssues.contains(issue))
            throw AssertionError("Expected issue was not found: $issue.")
        println("Issue was found!")
    }
}

private class NoUnexpectedErrorsCheckDescriptor(parentId: UniqueId)
    : AbstractTestDescriptor(
        parentId.append<NoUnexpectedErrorsCheckDescriptor>("no"),
        "Should not have any unexpected errors") {
    override fun getType() = TestDescriptor.Type.TEST

    fun execute(result: TestResult) {
        println("Check if there are any unexpected errors.")
        if (result.unexpectedErrors.any()) {
            val failureMessage = StringBuilder().apply {
                appendLine("The resource has unexpected errors:")
                result.unexpectedErrors.forEach { appendLine("- $it") }
            }.toString()

            throw AssertionError(failureMessage)
        }
        println("No unexpected errors!")
    }
}

private inline fun <T : TestDescriptor> EngineExecutionListener.scope(testDescriptor: T, execute: () -> Unit) {
    try {
        executionStarted(testDescriptor)
        execute()
        executionFinished(testDescriptor, TestExecutionResult.successful())
    }
    catch (error: Throwable) {
        executionFinished(testDescriptor, TestExecutionResult.failed(error))
    }
}

private inline fun <reified T> UniqueId.append(value: String) = append(T::class.simpleName, value)
