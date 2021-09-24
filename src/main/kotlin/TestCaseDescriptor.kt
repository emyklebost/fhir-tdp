package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
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
            println("> " + Theme.title.format("TEST: $displayName"))
            println("  Location: ${fileSource.toUrl()}")
            if (tags.any()) { println(Theme.tags.format("  Tags: ${tags.joinToString { it.name }}")) }

            val specFile = fileSource.file.toPath()
            val resourcePath = specFile.resolveAndNormalize(Path(testCase.source))
            val outcome = validator.validate(resourcePath, testCase.profile)

            val failures = UnexpectedIssue.test(testCase, outcome) + MissingIssue.test(testCase, outcome)
            println(createSummary(outcome, failures))

            if (failures.any()) {
                listener.reportingEntryPublished(this, createReportEntry(testCase, specFile))
                throw if (failures.count() == 1) failures.single() else MultipleFailuresError(null, failures)
            }
        }
}

private fun FileSource.toUrl() = "${file.toPath().toUri()}:${position.get().line}:${position.get().column.get()}"

private fun createReportEntry(testCase: Specification.TestCase, specFile: Path) =
    testCase.run {
        val values = mapOf(
            Pair(Specification.TestCase::source.name, "${specFile.resolveAndNormalize(Path(source)).toUri()}"),
            Pair(Specification.TestCase::profile.name, profile ?: "core"),
            Pair("${Specification.TestCase::expectedIssues.name}Count", "${expectedIssues.count()}")
        )

        ReportEntry.from(values)
    }

private fun createSummary(outcome: OperationOutcome, failedAssertions: List<AssertionFailedError>) =
    StringBuilder().run {
        val errors = outcome.issue.count { it.severity in listOf(Severity.ERROR, Severity.FATAL) }
        val warnings = outcome.issue.count { it.severity == Severity.WARNING }
        val infos = outcome.issue.count { it.severity == Severity.INFORMATION }

        appendLine("  Finished: $errors errors, $warnings warnings, $infos notes")

        val unexpectedIssues = failedAssertions.mapNotNull { (it as? UnexpectedIssue)?.issue }
        val missingIssues = failedAssertions.mapNotNull { (it as? MissingIssue)?.issue }

        outcome.issue.forEachIndexed { i, it ->
            val issue = it.toData()
            append("${i + 1}", issue, it.sourceUrl(), unexpectedIssues.any { mi -> mi.semanticallyEquals(issue) })
        }

        missingIssues.forEach {
            append("X", it, "N/A", true)
        }

        toString()
    }

private fun StringBuilder.append(mark: String, issue: Specification.Issue, source: String?, fail: Boolean) {
    val f = if (fail) Theme.fail else Theme.info
    appendLine(f.format("  $mark. Source: $source"))
    appendLine(f.format("     Severity: ${issue.severity}"))
    appendLine(f.format("     Type: ${issue.type}"))
    appendLine(f.format("     Expression: ${issue.expression}"))
    appendLine(f.format("     Message: ${issue.message}"))
}
