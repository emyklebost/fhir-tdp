package no.nav.helse

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.model.StringType
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.hl7.fhir.validation.ValidationEngine
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: ValidationEngine,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.resource.toString(), source) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            val specFile = (source.get() as FileSource).file.toPath()
            val resourcePath = specFile.resolveAndNormalize(testCase.resource).toString()
            val outcome = validator.validate(resourcePath, listOf(testCase.profile))

            prettify(outcome)
            println(outcome.toJson())

            children
                .mapNotNull { it as? ExpectationDescriptor }
                .forEach { it.execute(listener, outcome) }
        }
}

private fun IBaseResource.toJson(): String {
    // Not thread safe, new instance must therefore be created.
    val parser = FhirContext
        .forCached(structureFhirVersionEnum)
        .newJsonParser()
        .setPrettyPrint(true)

    return parser.encodeResourceToString(this)
}

private fun prettify(outcome: OperationOutcome): OperationOutcome {
    outcome.text = null // <- Removed because this is just noise when displayed in a terminal.

    val file = outcome.getExtensionByUrl(ToolingExtensions.EXT_OO_FILE)?.valueStringType?.value
    if (file != null) {
        outcome.issue.forEach {
            val line = it.getExtensionByUrl(ToolingExtensions.EXT_ISSUE_LINE)?.valueIntegerType?.value
            val column = it.getExtensionByUrl(ToolingExtensions.EXT_ISSUE_COL)?.valueIntegerType?.value

            var fileUrl = file.replace("\\", "/")
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
