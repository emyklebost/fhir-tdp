package no.nav.helse

import org.hl7.fhir.r5.model.ImplementationGuide
import org.hl7.fhir.r5.model.StructureDefinition
import org.hl7.fhir.utilities.TimeTracker
import org.hl7.fhir.utilities.VersionUtilities.getCurrentVersion
import org.hl7.fhir.utilities.VersionUtilities.packageForVersion
import org.hl7.fhir.validation.cli.model.CliContext
import org.hl7.fhir.validation.cli.services.ValidationService
import org.hl7.fhir.validation.cli.utils.Params
import org.hl7.fhir.validation.cli.utils.QuestionnaireMode
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path

internal object ValidatorFactory {
    private val cache = ConcurrentHashMap<Specification.Validator, Validator>()

    fun create(config: Specification.Validator, specFilePath: Path): Validator =
        cache.getOrPut(config) {
            val service = ValidationService()
            val ctx = config.withResolvedIgPaths(specFilePath).toCLIContext()

            // Must use good-old if-check because for some reason the Elvis operator doesn't work here.
            if (ctx.sv == null) ctx.sv = service.determineVersion(ctx)

            val definitions = "${packageForVersion(ctx.sv)}#${getCurrentVersion(ctx.sv)}"
            val engine = service.initializeValidator(ctx, definitions, TimeTracker())

            ctx.profiles.forEach {
                if (!engine.context.hasResource(StructureDefinition::class.java, it) &&
                    !engine.context.hasResource(ImplementationGuide::class.java, it)
                ) {
                    println("  Fetch Profile from $it")
                    engine.loadProfile(ctx.locations.getOrDefault(it, it))
                }
            }

            return Validator(engine)
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
