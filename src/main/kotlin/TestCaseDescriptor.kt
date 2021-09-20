package no.nav.helse

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.instance.model.api.IBaseResource
import org.hl7.fhir.validation.ValidationEngine
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource
import java.io.File

class TestSuiteDescriptor(id: UniqueId, name: String) : AbstractTestDescriptor(id, name) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            children
                .mapNotNull { it as? TestCaseDescriptor }
                .forEach { listener.scope(it) { it.execute(listener) } }
        }
}

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: ValidationEngine,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.resource, source) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            listener.reportingEntryPublished(this, ReportEntry.from("profile", testCase.profile))

            val specFile = (source.get() as FileSource).file
            val resourcePath = specFile.parentFile.resolve(File(testCase.resource)).toString()
            val outcome = validator.validate(resourcePath, listOf(testCase.profile))

            outcome.text = null // <- Removed because this is just noise when displayed in a terminal.
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
