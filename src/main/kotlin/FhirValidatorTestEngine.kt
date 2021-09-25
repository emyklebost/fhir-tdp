package no.nav

import org.junit.platform.engine.ConfigurationParameters
import org.junit.platform.engine.EngineDiscoveryRequest
import org.junit.platform.engine.ExecutionRequest
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DirectorySelector
import org.junit.platform.engine.discovery.FileSelector
import org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.streams.asSequence

class FhirValidatorTestEngine : HierarchicalTestEngine<FhirValidatorExecutionContext>() {
    // See https://junit.org/junit5/docs/current/user-guide/#launcher-api-engines-custom
    override fun getId() = "fhir-validator-junit"

    override fun discover(discoveryRequest: EngineDiscoveryRequest, uniqueId: UniqueId): TestDescriptor {
        val specFiles = discoveryRequest.run {
            val config = Config.create(configurationParameters)
            val fileExt = listOf("json", "yml", "yaml").map { "${config.postfix}.$it" }

            val files = getSelectorsByType(DirectorySelector::class.java)
                .map { it.path }
                .plus(listOfNotNull(config.selectDirectory))
                .flatMap { Files.walk(it).asSequence() }
                .filter { fileExt.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }

            files + getSelectorsByType(FileSelector::class.java).map { it.path }
        }

        return EngineDescriptorFactory.create(uniqueId, specFiles)
    }

    override fun createExecutionContext(request: ExecutionRequest) =
        FhirValidatorExecutionContext(request.engineExecutionListener)
}

// See https://junit.org/junit5/docs/current/user-guide/#running-tests-config-params
private data class Config(val selectDirectory: Path?, val postfix: String) {
    companion object {
        fun create(params: ConfigurationParameters) =
            params.run {
                Config(
                    get("no.nav.select-directory").run { if (isPresent) Path(get()) else null },
                    get("no.nav.test-postfix").run { if (isPresent) get() else "test" }
                )
            }
    }
}
