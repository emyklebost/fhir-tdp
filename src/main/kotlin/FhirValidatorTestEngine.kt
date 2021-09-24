package no.nav.helse

import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestEngine
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DirectorySelector
import org.junit.platform.engine.discovery.FileSelector
import java.nio.file.Files
import kotlin.io.path.name
import kotlin.streams.asSequence

class FhirValidatorTestEngine : TestEngine {
    override fun getId() = "fhir-validator"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val specFiles = discoveryRequest.run {
            val fileExt = listOf("test.json", "test.yml", "test.yaml")

            val files = getSelectorsByType(DirectorySelector::class.java)
                .map { it.path }
                .flatMap { Files.walk(it).asSequence() }
                .filter { fileExt.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }

            files + getSelectorsByType(FileSelector::class.java).map { it.path }
        }

        return EngineDescriptorFactory.create(uniqueId, specFiles)
    }

    override fun execute(request: ExecutionRequest) {
        println() // empty line for readability

        val engineDesc = request.rootTestDescriptor
        val listener = request.engineExecutionListener

        listener.scope(engineDesc) {
            engineDesc.children
                .mapNotNull { it as? TestSuiteDescriptor }
                .forEach { it.execute(listener) }
        }
    }
}
