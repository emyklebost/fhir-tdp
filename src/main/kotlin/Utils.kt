package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import java.nio.file.Path
import kotlin.io.path.isDirectory

typealias Severity = OperationOutcome.IssueSeverity
typealias IssueType = OperationOutcome.IssueType
typealias IssueComponent = OperationOutcome.OperationOutcomeIssueComponent

// Inlined to tidy up the stack-trace of failed tests.
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
