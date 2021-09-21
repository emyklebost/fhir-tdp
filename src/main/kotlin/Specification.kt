package no.nav.helse

import java.nio.file.Path

data class Specification(
    val validator: Validator = Validator(),
    val testCases: List<TestCase> = emptyList()
) {
    data class TestCase(
        val resource: Path,
        val profile: String,
        val expectedIssues: List<Issue> = emptyList()
    )

    data class Issue(
        val severity: Severity,
        val type: IssueType?,
        val expression: String?,
        val message: String?
    )

    data class Validator(
        val version: String? = null,
        val terminologyService: String? = null,
        val terminologyServiceLog: String? = null,
        val snomedCtEdition: String? = null,
        val igs: List<String> = emptyList()
    )
}
