package no.nav.helse

import org.hl7.fhir.utilities.validation.ValidationMessage
import org.hl7.fhir.validation.cli.model.FileInfo
import org.hl7.fhir.validation.cli.model.ValidationOutcome
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

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: FhirValidator,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.name ?: testCase.source, source) {
    override fun getType() = TestDescriptor.Type.TEST
    override fun getTags() = testCase.tags.map(TestTag::create).toSet()
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            val specFile = (source.get() as FileSource).file.toPath()
            val resourcePath = specFile.resolveAndNormalize(Path(testCase.source))
            val outcome = validator.validate(resourcePath, testCase.profile)

            val failures = testForUnexpectedErrors(testCase, outcome) + testForMissingExpectedIssues(testCase, outcome)

            if (failures.any()) {
                listener.reportingEntryPublished(this, createReportEntry(testCase, specFile))
                throw if (failures.count() == 1) failures.single() else MultipleFailuresError(null, failures)
            }
        }
}

private fun testForUnexpectedErrors(testCase: Specification.TestCase, outcome: ValidationOutcome): List<AssertionFailedError> {
    val unexpectedErrorFailures = outcome.messages
        .map { Pair(it.toData(), it.sourceUrl(outcome.fileInfo)) }
        .filterNot { it.first.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
        .filterNot { testCase.expectedIssues.any { expected -> expected.semanticallyEquals(it.first) } }
        .map { AssertionFailedError("Unexpected ${it.first} at ${it.second}") }

    println("  ${unexpectedErrorFailures.count()} unexpected error(s)!")

    return unexpectedErrorFailures
}

private fun testForMissingExpectedIssues(testCase: Specification.TestCase, outcome: ValidationOutcome): List<AssertionFailedError> {
    val issues = outcome.messages.map { it.toData() }

    val missingIssueFailures = testCase.expectedIssues
        .filterNot { expected -> issues.any { expected.semanticallyEquals(it) } }
        .map { AssertionFailedError("Expected issue was not found: $it.") }

    val foundCount = testCase.expectedIssues.count() - missingIssueFailures.count()
    println("  Found $foundCount of ${testCase.expectedIssues.count()} expected issue(s)!")

    return missingIssueFailures
}

private fun ValidationMessage.toData() = Specification.Issue(level, type, location, message)
private fun ValidationMessage.sourceUrl(file: FileInfo) = "${Path(file.fileName).toUri()}:$line:$col"

private fun Specification.Issue.semanticallyEquals(other: Specification.Issue): Boolean {
    if (severity != other.severity) return false
    if (type != null && type != other.type) return false
    if (expression != null && expression != other.expression) return false
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
