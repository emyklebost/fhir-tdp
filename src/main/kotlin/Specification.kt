package no.nav

import java.nio.file.Path

data class Specification(
    val name: String?,
    val validator: Validator = Validator(),
    val tests: List<TestCase> = emptyList()
) {
    data class TestCase(
        val source: Path,
        val name: String?,
        val profile: String?,
        val expectedIssues: List<Issue> = emptyList(),
        val tags: List<String> = emptyList()
    )

    data class Issue(
        val severity: Severity,
        val type: IssueType?,
        val expression: String?,
        val message: String?
    ) {
        fun semanticallyEquals(other: Issue): Boolean {
            if (severity != other.severity) return false
            if (type != null && type != other.type) return false
            if (expression != null && !expression.contentEquals(other.expression, ignoreCase = true)) return false
            return (message == null || (other.message?.contains(message, ignoreCase = true) == true))
        }
    }

    data class Validator(
        val version: String? = null,
        val terminologyService: String? = "n/a",
        val terminologyServiceLog: String? = null,
        val snomedCtEdition: String? = null,
        val igs: List<String> = emptyList()
    )
}
