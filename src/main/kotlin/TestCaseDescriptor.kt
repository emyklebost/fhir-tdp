package no.nav

import org.hl7.fhir.r5.model.OperationOutcome
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestTag
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import org.junit.platform.engine.support.hierarchical.Node
import org.opentest4j.AssertionFailedError
import org.opentest4j.MultipleFailuresError
import kotlin.io.path.nameWithoutExtension

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: FhirValidator,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.name ?: testCase.source.nameWithoutExtension, source),
    Node<FhirValidatorExecutionContext> {
    override fun getType() = TestDescriptor.Type.TEST
    override fun getTags() = testCase.tags.map(TestTag::create).toSet()
    override fun execute(
        context: FhirValidatorExecutionContext,
        dynamicTestExecutor: Node.DynamicTestExecutor?
    ): FhirValidatorExecutionContext {
        println("> " + Color.TITLE.paint("TEST: $displayName"))
        println("  Location: ${(source.get() as FileSource).toUrl()}")
        if (tags.any()) { println(Color.TAGS.paint("  Tags: ${tags.joinToString { it.name }}")) }

        val outcome = validator.validate(testCase.source, testCase.profile)

        val failures = UnexpectedIssue.test(testCase, outcome) + MissingIssue.test(testCase, outcome)
        println(createSummary(outcome, failures))

        if (failures.any()) {
            context.listener.reportingEntryPublished(this, createReportEntry(testCase))
            throw if (failures.count() == 1) failures.single() else MultipleFailuresError(null, failures)
        }

        return context
    }
}

private fun FileSource.toUrl() = "${file.toPath().toUri()}:${position.get().line}:${position.get().column.get()}"

private fun createReportEntry(testCase: Specification.TestCase) =
    testCase.run {
        val values = mapOf(
            Pair(Specification.TestCase::source.name, "${source.toUri()}"),
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
    val color = if (fail) Color.FAILED else Color.INFO
    appendLine(color.paint("  $mark. Source: $source"))
    appendLine(color.paint("     Severity: ${issue.severity}"))
    appendLine(color.paint("     Type: ${issue.type}"))
    appendLine(color.paint("     Expression: ${issue.expression}"))
    appendLine(color.paint("     Message: ${issue.message}"))
}
