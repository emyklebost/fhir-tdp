package no.nav.helse

data class Specification(
    val name: String?,
    val validator: Validator = Validator(),
    val tests: List<TestCase> = emptyList()
) {
    data class TestCase(
        val source: String,
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
    )

    data class Validator(
        val version: String? = null,
        val terminologyService: String? = "n/a",
        val terminologyServiceLog: String? = null,
        val snomedCtEdition: String? = null,
        val igs: List<String> = emptyList()
    )
}
