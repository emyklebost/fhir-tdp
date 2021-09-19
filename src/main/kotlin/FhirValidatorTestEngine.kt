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
        val rootDesc = EngineDescriptor(uniqueId, "FHIR Validator Tests")

        val specFiles = mutableListOf<File>()

        discoveryRequest.apply {
            specFiles.addAll(getSelectorsByType(FileSelector::class.java).map { it.file })

            val files = getSelectorsByType(DirectorySelector::class.java)
                .map { it.directory }
                .ifEmpty { listOf(File("src/test/resources")) }
                .flatMap { it.walk() }
                .filter { it.endsWith(".tdp.json") || it.endsWith(".tdp.yml") || it.endsWith(".tdp.yaml") }

            specFiles.addAll(files)
        }

        if (specFiles.isEmpty())
            specFiles.add(File("src/test/resources/tests.tdp.json"))

        specFiles.forEach {
            val spec = ConfigLoader().loadConfigOrThrow<Specification>(it)
            val testSuite = TestSuiteDescriptor(rootDesc.uniqueId, it.name, spec)
            rootDesc.addChild(testSuite)
        }

        return rootDesc
    }

    override fun execute(request: ExecutionRequest) {
        val testRoot = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.scope(testRoot) {
            testRoot.children
                .mapNotNull { it as? TestSuiteDescriptor }
                .forEach { testSuite -> listener.scope(testSuite) { it.execute(listener) } }
        }
    }
}

private class TestSuiteDescriptor(engineId: UniqueId, displayName: String, private val spec: Specification)
    : AbstractTestDescriptor(engineId.append(TestSuiteDescriptor::class.simpleName, displayName), displayName) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    init {
        spec.testCases.forEach {
            val testCase = TestCaseDescriptor(uniqueId, it.resource, it)
            addChild(testCase)
        }
    }

    fun execute(listener: EngineExecutionListener) {
        val validator = ValidatorFactory.create(spec.validator)
        val testRunner = ProfileValidationTest(validator)

        children
            .mapNotNull { it as? TestCaseDescriptor }
            .forEach { testCase -> listener.scope(testCase) { it.execute(testRunner, listener) } }
    }
}

private class TestCaseDescriptor(engineId: UniqueId, displayName: String, private val testCase: Specification.TestCase)
    : AbstractTestDescriptor(engineId.append(TestCaseDescriptor::class.simpleName, displayName), displayName) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    init {
        testCase.expectedIssues.forEachIndexed { i, it ->
            val id = uniqueId.append(ExpectedIssueCheckDescriptor::class.simpleName, i.toString())
            val expectedIssue = ExpectedIssueCheckDescriptor(id, it)
            addChild(expectedIssue)
        }

        val id = uniqueId.append(NoUnexpectedErrorsCheckDescriptor::class.simpleName, "no")
        addChild(NoUnexpectedErrorsCheckDescriptor(id))
    }

    fun execute(testRunner: ProfileValidationTest, listener: EngineExecutionListener) {
        val result = testRunner.run(testCase)

        children
            .mapNotNull { it as? ExpectedIssueCheckDescriptor }
            .forEach { check -> listener.scope(check) { it.execute(result) } }

        children
            .mapNotNull { it as? NoUnexpectedErrorsCheckDescriptor }
            .forEach { check -> listener.scope(check) { it.execute(result) } }
    }
}

private class ExpectedIssueCheckDescriptor(uniqueId: UniqueId, val issue: Specification.Issue)
    : AbstractTestDescriptor(uniqueId, "Should have issue with severity=${issue.severity}") {
    override fun getType() = TestDescriptor.Type.TEST

    fun execute(result: TestResult) {
        println("Check if validation result contains $issue.")
        if (result.missingIssues.contains(issue))
            throw AssertionError("Expected issue was not found: $issue.")
        println("Issue was found!")
    }
}

private class NoUnexpectedErrorsCheckDescriptor(uniqueId: UniqueId)
    : AbstractTestDescriptor(uniqueId, "Should not have any unexpected errors") {
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

private inline fun <T : TestDescriptor> EngineExecutionListener.scope(testDescriptor: T, execute: (T) -> Unit) {
    try {
        executionStarted(testDescriptor)
        execute(testDescriptor)
        executionFinished(testDescriptor, TestExecutionResult.successful())
    }
    catch (error: Throwable) {
        executionFinished(testDescriptor, TestExecutionResult.failed(error))
    }
}
