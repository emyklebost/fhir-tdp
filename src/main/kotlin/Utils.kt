package no.nav.helse

import com.diogonunes.jcolor.AnsiFormat
import com.diogonunes.jcolor.Attribute
import org.hl7.fhir.r5.model.OperationOutcome
import org.hl7.fhir.r5.utils.ToolingExtensions
import org.junit.platform.engine.EngineExecutionListener
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.TestExecutionResult
import java.nio.file.Path
import kotlin.io.path.isDirectory

typealias Severity = OperationOutcome.IssueSeverity
typealias IssueType = OperationOutcome.IssueType
typealias IssueComponent = OperationOutcome.OperationOutcomeIssueComponent

/** See https://github.com/dialex/JColor on how to customize.
 *  Trying to adhere to Junit TestExecutionSummary's coloring-scheme. */
object Theme {
    val title = AnsiFormat(Attribute.CYAN_TEXT())
    val tags = AnsiFormat(Attribute.YELLOW_TEXT())
    val pass = AnsiFormat(Attribute.GREEN_TEXT())
    val fail = AnsiFormat(Attribute.RED_TEXT())
    val info = AnsiFormat(Attribute.BLUE_TEXT())
}

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

fun IssueComponent.toData() = Specification.Issue(severity, code, expression.firstOrNull()?.asStringValue(), details.text)
fun IssueComponent.sourceUrl() = getExtensionByUrl(ToolingExtensions.EXT_ISSUE_SOURCE)?.valueStringType?.value
