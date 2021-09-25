package no.nav

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.reporting.ReportEntry
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.hierarchical.Node

class TestSuiteDescriptor(
    id: UniqueId,
    private val spec: Specification,
    source: TestSource
) : AbstractTestDescriptor(id, spec.name, source), Node<FhirValidatorExecutionContext> {
    override fun getType() = TestDescriptor.Type.CONTAINER
    override fun execute(
        context: FhirValidatorExecutionContext,
        dynamicTestExecutor: Node.DynamicTestExecutor
    ) = try {
        println(Color.TITLE.paint("# SUITE: $displayName"))
        context.copy(validator = FhirValidator.create(spec.validator))
    } catch (ex: Throwable) {
        context.listener.reportingEntryPublished(this, createReportEntry(spec.validator))
        println()
        throw ex
    } finally {
        println()
    }
}

private fun createReportEntry(spec: Specification.Validator) =
    spec.run {
        val values = mapOf(
            Pair(Specification.Validator::version.name, version),
            Pair(Specification.Validator::terminologyService.name, terminologyService),
            Pair(Specification.Validator::terminologyServiceLog.name, terminologyServiceLog),
            Pair(Specification.Validator::snomedCtEdition.name, snomedCtEdition),
            Pair(Specification.Validator::igs.name, if (igs.isEmpty()) null else igs.joinToString())
        ).filterValues { it != null }

        ReportEntry.from(values)
    }
