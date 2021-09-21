package no.nav.helse

import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import java.nio.file.Path
import kotlin.io.path.isDirectory

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

fun Path.resolveAndNormalize(path: Path): Path {
    if (path.isAbsolute) return path
    val dir = if (isDirectory()) this else parent
    return dir.resolve(path).normalize()
}
