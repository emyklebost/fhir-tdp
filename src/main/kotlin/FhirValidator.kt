package no.nav.helse

import org.hl7.fhir.r5.model.ImplementationGuide
import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.model.StringType
import org.hl7.fhir.r5.model.StructureDefinition
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.hl7.fhir.utilities.TimeTracker
import org.hl7.fhir.utilities.VersionUtilities
import org.hl7.fhir.validation.ValidationEngine
import org.hl7.fhir.validation.cli.model.CliContext
import org.hl7.fhir.validation.cli.services.ValidationService
import org.hl7.fhir.validation.cli.utils.Params
import org.hl7.fhir.validation.cli.utils.QuestionnaireMode
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

class FhirValidator(private val validationEngine: ValidationEngine) {
    private val cache = ConcurrentHashMap<Pair<Path, String?>, OperationOutcome>()

    fun validate(source: Path, profile: String?): OperationOutcome =
        cache.getOrPut(Pair(source, profile)) {
            val outcome = validationEngine.validate(source.toString(), listOf(profile).mapNotNull { it })
            prettify(outcome)
        }

    companion object {
        private val cache = ConcurrentHashMap<Specification.Validator, FhirValidator>()

        fun create(config: Specification.Validator, specFilePath: Path): FhirValidator {
            val resolvedConfig = config.withResolvedIgPaths(specFilePath)
            return cache.getOrPut(resolvedConfig) {
                val service = ValidationService()
                val ctx = resolvedConfig.toCLIContext()

                // Must use good-old if-check because for some reason the Elvis operator doesn't work here.
                if (ctx.sv == null) ctx.sv = service.determineVersion(ctx)

                val definitions = "${VersionUtilities.packageForVersion(ctx.sv)}#${
                VersionUtilities.getCurrentVersion(
                    ctx.sv
                )
                }"
                val engine = service.initializeValidator(ctx, definitions, TimeTracker())

                ctx.profiles.forEach {
                    if (!engine.context.hasResource(StructureDefinition::class.java, it) &&
                        !engine.context.hasResource(ImplementationGuide::class.java, it)
                    ) {
                        println("  Fetch Profile from $it")
                        engine.loadProfile(ctx.locations.getOrDefault(it, it))
                    }
                }

                FhirValidator(engine)
            }
        }
    }
}

private fun prettify(outcome: OperationOutcome): OperationOutcome {
    outcome.text = null // <- Removed because this is just noise when displayed in a terminal.

    val file = outcome.getExtensionByUrl(ToolingExtensions.EXT_OO_FILE)?.valueStringType?.value
    if (file != null) {
        outcome.issue.forEach {
            val line = it.getExtensionByUrl(ToolingExtensions.EXT_ISSUE_LINE)?.valueIntegerType?.value
            val column = it.getExtensionByUrl(ToolingExtensions.EXT_ISSUE_COL)?.valueIntegerType?.value

            var fileUrl = Path(file).toUri().toString()
            line?.let {
                fileUrl += ":$line"
                column?.let { fileUrl += ":$column" }
            }

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
