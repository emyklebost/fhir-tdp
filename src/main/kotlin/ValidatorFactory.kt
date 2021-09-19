package no.nav.helse

import org.hl7.fhir.r5.model.ImplementationGuide
import org.hl7.fhir.r5.model.StructureDefinition
import org.hl7.fhir.utilities.TimeTracker
import org.hl7.fhir.utilities.VersionUtilities.getCurrentVersion
import org.hl7.fhir.utilities.VersionUtilities.packageForVersion
import org.hl7.fhir.validation.ValidationEngine
import org.hl7.fhir.validation.cli.model.CliContext
import org.hl7.fhir.validation.cli.services.ValidationService
import org.hl7.fhir.validation.cli.utils.Params
import java.util.concurrent.ConcurrentHashMap

internal object ValidatorFactory {
    private val cache = ConcurrentHashMap<Specification.Validator, ValidationEngine>()

    fun create(config: Specification.Validator): ValidationEngine =
        cache.getOrPut(config) {
            val service = ValidationService()
            val ctx = config.toCLIContext()

            // Must use good-old if-check because for some reason the Elvis operator doesn't work here.
            if (ctx.sv == null) ctx.sv = service.determineVersion(ctx)

            val definitions = "${packageForVersion(ctx.sv)}#${getCurrentVersion(ctx.sv)}"
            val validator = service.initializeValidator(ctx, definitions, TimeTracker())

            ctx.profiles.forEach {
                if (!validator.context.hasResource(StructureDefinition::class.java, it) &&
                    !validator.context.hasResource(ImplementationGuide::class.java, it))
                {
                    println("  Fetch Profile from $it")
                    validator.loadProfile(ctx.locations.getOrDefault(it, it))
                }
            }

            return validator
        }
}

private fun Specification.Validator.toCLIContext(): CliContext {
    val args = mutableListOf<String>()

    igs.forEach { args.addAll(listOf(Params.IMPLEMENTATION_GUIDE, it)) }
    version?.let { args.addAll(listOf(Params.VERSION, it)) }
    snomedCtEdition?.let { args.addAll(listOf(Params.SCT, it)) }
    terminologyService?.let { args.addAll(listOf(Params.TERMINOLOGY, it)) }
    terminologyServiceLog?.let { args.addAll(listOf(Params.TERMINOLOGY_LOG, it)) }

    return Params.loadCliContext(args.toTypedArray())
}