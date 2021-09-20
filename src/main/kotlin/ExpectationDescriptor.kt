package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
import org.junit.platform.engine.*
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor

abstract class ExpectationDescriptor(
    id: UniqueId,
    name: String,
    source: TestSource? = null
) : AbstractTestDescriptor(id, name, source) {
    override fun getType() = TestDescriptor.Type.TEST
    abstract fun execute(listener: EngineExecutionListener, outcome: OperationOutcome)
}

class HasExpectedIssueDescriptor(
    id: UniqueId,
    private val issue: Specification.Issue,
    source: TestSource? = null
) : ExpectationDescriptor(id, "Should have issue with severity=${issue.severity}", source) {
    override fun execute(listener: EngineExecutionListener, outcome: OperationOutcome) =
        listener.scope(this) {
            println("Check if validation result contains $issue.")
            if (outcome.issue.map { it.toData() }.none { issue.matches(it) }) {
                throw AssertionError("Expected issue was not found: $issue.")
            }
            println("Expected issue was found!")
        }
}

class HasNoUnexpectedErrorsDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    source: TestSource? = null
) : ExpectationDescriptor(id, "Should not have any unexpected errors", source) {
    override fun execute(listener: EngineExecutionListener, outcome: OperationOutcome) =
        listener.scope(this) {
            println("Check if there are any unexpected errors.")

            val unexpectedErrors = outcome.issue
                .map { it.toData() }
                .filterNot { it.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
                .filterNot { testCase.expectedIssues.any { expected -> expected.matches(it) } }

            if (unexpectedErrors.any()) {
                val failureMessage = StringBuilder().apply {
                    appendLine("The resource has unexpected errors:")
                    unexpectedErrors.forEach { appendLine("- $it") }
                }.toString()

                throw AssertionError(failureMessage)
            }

            println("No unexpected errors!")
        }
}

private fun OperationOutcome.OperationOutcomeIssueComponent.toData() =
    Specification.Issue(severity, code, expression.joinToString("|") { it.asStringValue() }, details.text)

private fun Specification.Issue.matches(other: Specification.Issue): Boolean {
    if (severity != other.severity) return false
    if (type != null && type != other.type) return false
    if (location != null && location != other.location) return false
    return (message == null || (other.message?.contains(message, ignoreCase = true) == true))
}