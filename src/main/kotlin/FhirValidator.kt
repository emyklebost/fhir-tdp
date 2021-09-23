package no.nav.helse

import org.hl7.fhir.validation.cli.model.CliContext
import org.hl7.fhir.validation.cli.model.FileInfo
import org.hl7.fhir.validation.cli.model.ValidationOutcome
import org.hl7.fhir.validation.cli.model.ValidationRequest
import org.hl7.fhir.validation.cli.services.ValidationService
import org.hl7.fhir.validation.cli.utils.Params
import org.hl7.fhir.validation.cli.utils.QuestionnaireMode
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.readText

class FhirValidator(private val config: Specification.Validator) {
    fun validate(source: Path, profile: String?): ValidationOutcome {
        val cliContext = config.toCLIContext().apply {
            profile?.let { addProfile(profile) }
        }
        val fileInfo = FileInfo(source.toString(), source.readText(), source.extension)

        val request = ValidationRequest(cliContext, listOf(fileInfo), config.hashCode().toString())
        return service.validateSources(request).outcomes.single()
    }

    companion object {
        private val service = ValidationService()

        fun create(config: Specification.Validator, specFilePath: Path) =
            FhirValidator(config.withResolvedIgPaths(specFilePath))
    }
}

private fun Specification.Validator.toCLIContext(): CliContext {
    val args = mutableListOf<String>()

    args.add(Params.STRICT_EXTENSIONS)
    args.addAll(listOf(Params.QUESTIONNAIRE, QuestionnaireMode.REQUIRED.name))

    igs.forEach { args.addAll(listOf(Params.IMPLEMENTATION_GUIDE, it)) }
    version?.let { args.addAll(listOf(Params.VERSION, it)) }
    snomedCtEdition?.let { args.addAll(listOf(Params.SCT, it)) }
    terminologyService?.let { args.addAll(listOf(Params.TERMINOLOGY, it)) }
    terminologyServiceLog?.let { args.addAll(listOf(Params.TERMINOLOGY_LOG, it)) }

    return Params.loadCliContext(args.toTypedArray())
}

// An IG can be specified as either package, file, folder or url.
// In case of file or folder we want the path to be resolved relative to the specification file.
private fun Specification.Validator.withResolvedIgPaths(specFilePath: Path): Specification.Validator {
    val resolvedIgs = igs.map {
        try {
            specFilePath.resolveAndNormalize(Path(it)).toString()
        } catch (ex: Throwable) {
            it
        }
    }

    return copy(igs = resolvedIgs)
}
