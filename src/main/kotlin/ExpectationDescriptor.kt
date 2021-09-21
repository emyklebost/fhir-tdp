package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.opentest4j.AssertionFailedError

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
) : ExpectationDescriptor(id, "Should have expected issue: ${id.lastSegment.value}", source) {
    override fun execute(listener: EngineExecutionListener, outcome: OperationOutcome) =
        listener.scope(this) {
            println("Check if validation result contains $issue.")

            if (outcome.issue.map { it.toData() }.none { issue.matches(it) }) {
                listener.reportingEntryPublished(this, ReportEntry.from(issue.asMap()))
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
                .map { Pair(it.toData(), it.sourceUrl()) }
                .filterNot { it.first.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
                .filterNot { testCase.expectedIssues.any { expected -> expected.matches(it.first) } }

            if (unexpectedErrors.any()) {
                val failureMessage = StringBuilder().apply {
                    unexpectedErrors.forEach {
                        appendLine("Unexpected ${it.first} at ${it.second}")
                    }
                }.toString()

                throw AssertionFailedError(failureMessage)
            }

            println("No unexpected errors!")
        }
}

private fun IssueComponent.toData() =
    Specification.Issue(severity, code, expression.joinToString("|") { it.asStringValue() }, details.text)

private fun IssueComponent.sourceUrl() =
    getExtensionByUrl(ToolingExtensions.EXT_ISSUE_SOURCE)?.valueStringType?.value

private fun Specification.Issue.matches(other: Specification.Issue): Boolean {
    if (severity != other.severity) return false
    if (type != null && type != other.type) return false
    if (expression != null && expression != other.expression) return false
    return (message == null || (other.message?.contains(message, ignoreCase = true) == true))
}

private fun Specification.Issue.asMap() =
    mapOf(
        Pair("severity", severity.toCode().uppercase()),
        Pair("type", type?.toCode()?.uppercase()),
        Pair("expression", expression),
        Pair("message", message),
    ).filterValues { it != null }
