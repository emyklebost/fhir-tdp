package no.nav.helse

import org.hl7.fhir.r5.model.OperationOutcome
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.FilePosition
import org.junit.platform.engine.support.descriptor.FileSource
import java.nio.file.Path
import kotlin.io.path.forEachLine
import kotlin.io.path.isDirectory
import kotlin.io.path.name

typealias Severity = OperationOutcome.IssueSeverity
typealias IssueType = OperationOutcome.IssueType
typealias IssueComponent = OperationOutcome.OperationOutcomeIssueComponent

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

/** Creates a FileSource with FilePosition (line, column) of the 'source' property
 ** of the Test with the specified index. Works with both json and yaml files. */
fun Path.fileSource(testIndex: Int): FileSource {
    val pattern = if (name.endsWith(".json")) "\"source\"" else "[ {]source: "
    val filePosition = matches(Regex(pattern)).elementAtOrNull(testIndex)
    return FileSource.from(toFile(), filePosition)
}

private fun Path.matches(pattern: Regex) =
    sequence<FilePosition> {
        var lineNr = 1
        forEachLine { line ->
            pattern.findAll(line).forEach {
                yield(FilePosition.from(lineNr, it.range.first + 1))
            }
            lineNr++
        }
    }
