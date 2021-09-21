package no.nav.helse

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import java.nio.file.Path

inline fun EngineExecutionListener.scope(testDescriptor: TestDescriptor, execute: () -> Unit) {
    try {
        executionStarted(testDescriptor)
        execute()
        executionFinished(testDescriptor, TestExecutionResult.successful())
    } catch (error: Throwable) {
        executionFinished(testDescriptor, TestExecutionResult.failed(error))
    }
}

inline fun <reified T> UniqueId.append(value: String) = append(T::class.simpleName, value)!!

fun resolvePathRelativeToSpecFile(specFilePath: Path, path: Path): Path {
    if (path.isAbsolute) return path
    return specFilePath.parent.resolve(path).normalize()
}
