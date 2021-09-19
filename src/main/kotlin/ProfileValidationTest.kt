package no.nav.helse

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.validation.ValidationEngine

data class TestResult(
    val missingIssues: List<Specification.Issue>,
    val unexpectedErrors: List<Specification.Issue>)

class ProfileValidationTest(private val validator: ValidationEngine) {
    fun run(case: Specification.TestCase): TestResult {
        repeat(2) { println() } // Empty lines for log readability.

        val outcome = validator.validate("src/test/resources/${case.resource}", listOf(case.profile))
        val issues = outcome.issue.map { it.toData() }

        outcome.text = null // <- Removed because this is just noise when displayed in a terminal.
        println(outcome.toJson())

        val missingIssues = case.expectedIssues
            .filterNot { expected -> issues.any { expected.matches(it) } }
        val unexpectedErrors = issues
            .filterNot { it.severity in listOf(Severity.INFORMATION, Severity.WARNING) }
            .filterNot { case.expectedIssues.any { expected -> expected.matches(it) } }

        return TestResult(missingIssues, unexpectedErrors)
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

private fun IBaseResource.toJson(): String {
    // Not thread safe, new instance must therefore be created.
    val parser = FhirContext
        .forCached(structureFhirVersionEnum)
        .newJsonParser()
        .setPrettyPrint(true)

    return parser.encodeResourceToString(this)
}
