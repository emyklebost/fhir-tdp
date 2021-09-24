package no.nav.helse

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.FileSource

class TestSuiteDescriptor(id: UniqueId, name: String, source: FileSource) :
    AbstractTestDescriptor(id, name, source) {
    override fun getType() = TestDescriptor.Type.CONTAINER
    fun execute(listener: EngineExecutionListener) =
        listener.scope(this) {
            children
                .mapNotNull { it as? TestCaseDescriptor }
                .forEach { it.execute(listener) }
        }
}
