import no.nav.helse.FhirValidatorTestEngine
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectDirectory
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
}