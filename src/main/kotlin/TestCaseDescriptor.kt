package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestTag
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import org.opentest4j.AssertionFailedError
import org.opentest4j.MultipleFailuresError
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.nameWithoutExtension

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: FhirValidator,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.name ?: Path(testCase.source).nameWithoutExtension, source) {
    override fun getType() = TestDescriptor.Type.TEST
    override fun getTags() = testCase.tags.map(TestTag::create).toSet()
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            val fileSource = (source.get() as FileSource)
            println("> TEST: $displayName")
            println("  Location: ${fileSource.toUrl()}")

            val specFile = fileSource.file.toPath()
            val resourcePath = specFile.resolveAndNormalize(Path(testCase.source))
            val outcome = validator.validate(resourcePath, testCase.profile)

            val failures = testForUnexpectedErrors(testCase, outcome) + testForMissingExpectedIssues(testCase, outcome)
            println(createSummary(outcome))

            if (failures.any()) {
                listener.reportingEntryPublished(this, createReportEntry(testCase, specFile))
                throw if (failures.count() == 1) failures.single() else MultipleFailuresError(null, failures)
            }
        }
}

private fun testForUnexpectedErrors(testCase: Specification.TestCase, outcome: OperationOutcome): List<AssertionFailedError> {
    val unexpectedErrorFailures = outcome.issue
        .map { Pair(it.toData(), it.sourceUrl()) }
        .filterNot { it.first.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
        .filterNot { testCase.expectedIssues.any { expected -> expected.semanticallyEquals(it.first) } }
        .map { AssertionFailedError("Unexpected ${it.first} at ${it.second}") }

    println("  ${unexpectedErrorFailures.count()} unexpected error(s)!")

    return unexpectedErrorFailures
}

private fun testForMissingExpectedIssues(testCase: Specification.TestCase, outcome: OperationOutcome): List<AssertionFailedError> {
    val issues = outcome.issue.map { it.toData() }

    val missingIssueFailures = testCase.expectedIssues
        .filterNot { expected -> issues.any { expected.semanticallyEquals(it) } }
        .map { AssertionFailedError("Expected issue was not found: $it.") }

    val foundCount = testCase.expectedIssues.count() - missingIssueFailures.count()
    println("  Found $foundCount of ${testCase.expectedIssues.count()} expected issue(s)!")

    return missingIssueFailures
}

private fun IssueComponent.toData() = Specification.Issue(severity, code, expression.firstOrNull()?.asStringValue(), details.text)
private fun IssueComponent.sourceUrl() = getExtensionByUrl(ToolingExtensions.EXT_ISSUE_SOURCE)?.valueStringType?.value
private fun FileSource.toUrl() = "${file.toPath().toUri()}:${position.get().line}:${position.get().column.get()}"

private fun Specification.Issue.semanticallyEquals(other: Specification.Issue): Boolean {
    if (severity != other.severity) return false
    if (type != null && type != other.type) return false
    if (expression != null && !expression.contentEquals(other.expression, ignoreCase = true)) return false
    return (message == null || (other.message?.contains(message, ignoreCase = true) == true))
}

private fun createReportEntry(testCase: Specification.TestCase, specFile: Path) =
    testCase.run {
        val values = mapOf(
            Pair(Specification.TestCase::source.name, "${specFile.resolveAndNormalize(Path(source)).toUri()}"),
            Pair(Specification.TestCase::profile.name, profile),
            Pair("${Specification.TestCase::expectedIssues.name}Count", "${expectedIssues.count()}")
        )

        ReportEntry.from(values)
    }

private fun createSummary(outcome: OperationOutcome) =
    StringBuilder().run {
        val errors = outcome.issue.count { it.severity in listOf(Severity.ERROR, Severity.FATAL) }
        val warnings = outcome.issue.count { it.severity == Severity.WARNING }
        val infos = outcome.issue.count { it.severity == Severity.INFORMATION }

        appendLine("  Finished: $errors errors, $warnings warnings, $infos notes")
        outcome.issue.forEachIndexed { i, it ->
            val issue = it.toData()
            appendLine("  ${i + 1}. Source: ${it.sourceUrl()}")
            appendLine("     Severity: ${issue.severity}")
            appendLine("     Type: ${issue.type}")
            appendLine("     Expression: ${issue.expression}")
            appendLine("     Message: ${issue.message}")
        }

        toString()
    }
