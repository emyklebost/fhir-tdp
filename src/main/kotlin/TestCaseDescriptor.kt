package no.nav.helse

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import org.opentest4j.AssertionFailedError
import org.opentest4j.MultipleFailuresError
import java.nio.file.Path
import kotlin.io.path.Path

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: Validator,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.resource, source) {
    override fun getType() = TestDescriptor.Type.TEST
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            val specFile = (source.get() as FileSource).file.toPath()
            val resourcePath = specFile.resolveAndNormalize(Path(testCase.resource))
            val outcome = validator.validate(resourcePath, testCase.profile)

            val failures =
                testForUnexpectedErrors(testCase, outcome) + testForMissingExpectedIssues(testCase, outcome)

            println(outcome.toJson())

            if (failures.any()) {
                listener.reportingEntryPublished(this, createReportEntry(testCase, specFile))
                throw if (failures.count() == 1) failures.single() else MultipleFailuresError(null, failures)
            }
        }
}

private fun IBaseResource.toJson(): String {
    // Not thread safe, new instance must therefore be created.
    val parser = FhirContext
        .forCached(structureFhirVersionEnum)
        .newJsonParser()
        .setPrettyPrint(true)

    return parser.encodeResourceToString(this)
}

private fun testForUnexpectedErrors(testCase: Specification.TestCase, outcome: OperationOutcome): List<AssertionFailedError> {
    val unexpectedErrorFailures = outcome.issue
        .map { Pair(it.toData(), it.sourceUrl()) }
        .filterNot { it.first.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
        .filterNot { testCase.expectedIssues.any { expected -> expected.matches(it.first) } }
        .map { AssertionFailedError("Unexpected ${it.first} at ${it.second}") }

    println("  ${unexpectedErrorFailures.count()} unexpected error(s)!")

    return unexpectedErrorFailures
}

private fun testForMissingExpectedIssues(testCase: Specification.TestCase, outcome: OperationOutcome): List<AssertionFailedError> {
    val issues = outcome.issue.map { it.toData() }

    val missingIssueFailures = testCase.expectedIssues
        .filter { expected -> issues.any { expected.matches(it) } }
        .map { AssertionFailedError("Expected issue was not found: $it.") }

    val foundCount = testCase.expectedIssues.count() - missingIssueFailures.count()
    println("  Found $foundCount of ${testCase.expectedIssues.count()} expected issue(s)!")

    return missingIssueFailures
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

private fun createReportEntry(testCase: Specification.TestCase, specFile: Path) =
    testCase.run {
        val values = mapOf(
            Pair("resource", "${specFile.resolveAndNormalize(Path(resource)).toUri()}"),
            Pair("profile", profile),
            Pair("expectedIssuesCount", "${expectedIssues.count()}")
        )

        ReportEntry.from(values)
    }
