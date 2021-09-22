package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.model.StringType
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.hl7.fhir.validation.ValidationEngine
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

class Validator(private val validationEngine: ValidationEngine) {
    private val cache = ConcurrentHashMap<Pair<Path, String?>, OperationOutcome>()

    fun validate(source: Path, profile: String?) =
        cache.getOrPut(Pair(source, profile)) {
            val outcome = validationEngine.validate(source.toString(), listOf(profile).mapNotNull { it })
            prettify(outcome)
        }!!
}

private fun prettify(outcome: OperationOutcome): OperationOutcome {
    outcome.text = null // <- Removed because this is just noise when displayed in a terminal.

    val file = outcome.getExtensionByUrl(ToolingExtensions.EXT_OO_FILE)?.valueStringType?.value
    if (file != null) {
        outcome.issue.forEach {
            val line = it.getExtensionByUrl(ToolingExtensions.EXT_ISSUE_LINE)?.valueIntegerType?.value
            val column = it.getExtensionByUrl(ToolingExtensions.EXT_ISSUE_COL)?.valueIntegerType?.value

            // This format seems to be resolvable (clickable) within the vscode console.
            var fileUrl = Path(file).toUri().toString().removePrefix("file:///")
            line?.let { fileUrl += ":$line" }
            column?.let { fileUrl += ":$column" }

            listOf(
                ToolingExtensions.EXT_ISSUE_LINE,
                ToolingExtensions.EXT_ISSUE_COL,
                ToolingExtensions.EXT_ISSUE_SOURCE
            ).forEach { extUrl -> it.removeExtension(extUrl) }

            it.addExtension(ToolingExtensions.EXT_ISSUE_SOURCE, StringType(fileUrl))
        }
    }

    return outcome
}
