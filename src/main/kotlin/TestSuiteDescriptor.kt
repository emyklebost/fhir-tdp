package no.nav

import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestSource
import org.junit.platform.engine.UniqueId
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
    ) = context.copy(validator = FhirValidator.create(spec.validator)).apply {
        println() // Empty line for readability
    }
}
