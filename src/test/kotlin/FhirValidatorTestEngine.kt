import no.nav.helse.FhirValidatorTestEngine
import org.junit.jupiter.api.Test
import org.junit.platform.engine.discovery.DiscoverySelectors.selectDirectory
import org.junit.platform.testkit.engine.EngineTestKit

class FhirValidatorTestEngineTest {
    @Test
    fun `Given a valid Test file, tests should be discovered and executed`() {
        EngineTestKit
            .engine(FhirValidatorTestEngine())
            .selectors(selectDirectory("src/test/resources"))
            .execute()
            .testEvents()
            .assertStatistics {
                it.started(4).succeeded(3).failed(1)
            }
    }
}
