package no.nav.helse

import ca.uhn.fhir.context.FhirContext
import org.hl7.fhir.instance.model.api.IBaseResource
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource

class TestCaseDescriptor(
    id: UniqueId,
    private val testCase: Specification.TestCase,
    private val validator: Validator,
    source: FileSource,
) : AbstractTestDescriptor(id, testCase.resource.toString(), source) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            val specFile = (source.get() as FileSource).file.toPath()
            val resourcePath = specFile.resolveAndNormalize(testCase.resource)
            val outcome = validator.validate(resourcePath, testCase.profile)

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
