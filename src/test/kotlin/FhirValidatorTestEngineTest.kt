import no.nav.helse.FhirValidatorTestEngine
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectDirectory
import org.junit.platform.engine.discovery.DiscoverySelectors.selectFile
import org.junit.platform.testkit.engine.EngineTestKit

class FhirValidatorTestEngineTest {
    @Test
    fun `Given a valid Test file in a sub-dir, tests should be discovered and executed`() {
        EngineTestKit
            .engine(FhirValidatorTestEngine())
            .selectors(selectDirectory("src/test/resources/subdir"))
            .execute()
            .testEvents()
            .assertStatistics {
                it.started(3).succeeded(1).failed(2).aborted(0).skipped(0)
            }
    }

    @Test
    fun `Given a yaml test file, tests should be discovered and executed`() {
        EngineTestKit
            .engine(FhirValidatorTestEngine())
            .selectors(selectFile("src/test/resources/simple.test.yaml"))
            .execute()
            .testEvents()
            .assertStatistics {
                it.started(1).succeeded(1).failed(0).aborted(0).skipped(0)
            }
    }
}
