package no.nav.helse

import org.hl7.fhir.utilities.validation.ValidationMessage
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import java.nio.file.Path
import kotlin.io.path.isDirectory

typealias Severity = ValidationMessage.IssueSeverity
typealias IssueType = ValidationMessage.IssueType

inline fun EngineExecutionListener.scope(testDescriptor: TestDescriptor, execute: () -> Unit) {
    try {
        executionStarted(testDescriptor)
        execute()
        executionFinished(testDescriptor, TestExecutionResult.successful())
    } catch (error: Throwable) {
        executionFinished(testDescriptor, TestExecutionResult.failed(error))
    }
}

fun Path.resolveAndNormalize(path: Path): Path {
    if (path.isAbsolute) return path
    val dir = if (isDirectory()) this else parent
    return dir.resolve(path).normalize()
}
