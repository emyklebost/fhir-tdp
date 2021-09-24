package no.nav.helse

import com.diogonunes.jcolor.Ansi.colorize
import com.diogonunes.jcolor.Attribute.GREEN_TEXT
import com.diogonunes.jcolor.Attribute.RED_TEXT
import org.hl7.fhir.r5.model.OperationOutcome
import org.opentest4j.AssertionFailedError

class UnexpectedIssue(val issue: Specification.Issue, val source: String?) : AssertionFailedError("Unexpected $issue at $source") {
    companion object {
        fun test(testCase: Specification.TestCase, outcome: OperationOutcome): List<UnexpectedIssue> {
            val unexpectedErrorFailures = outcome.issue
                .map { UnexpectedIssue(it.toData(), it.sourceUrl()) }
                .filterNot { it.issue.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
                .filterNot { testCase.expectedIssues.any { expected -> expected.semanticallyEquals(it.issue) } }

            val color = if (unexpectedErrorFailures.isEmpty()) GREEN_TEXT() else RED_TEXT()
            println(colorize("  ${unexpectedErrorFailures.count()} unexpected error(s)!", color))

            return unexpectedErrorFailures
        }
    }
}

class MissingIssue(val issue: Specification.Issue) : AssertionFailedError("Expected issue was not found: $issue.") {
    companion object {
        fun test(testCase: Specification.TestCase, outcome: OperationOutcome): List<MissingIssue> {
            val issues = outcome.issue.map { it.toData() }

            val missingIssueFailures = testCase.expectedIssues
                .filterNot { expected -> issues.any { expected.semanticallyEquals(it) } }
                .map { MissingIssue(it) }

            val foundCount = testCase.expectedIssues.count() - missingIssueFailures.count()
            val color = if (foundCount == testCase.expectedIssues.count()) GREEN_TEXT() else RED_TEXT()
            println(colorize("  Found $foundCount of ${testCase.expectedIssues.count()} expected issue(s)!", color))

            return missingIssueFailures
        }
    }
}
