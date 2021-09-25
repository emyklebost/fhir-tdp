package no.nav

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.json.JsonParser
import com.sksamuel.hoplite.yaml.YamlParser
import org.junit.platform.engine.TestDescriptor
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor
import org.junit.platform.engine.support.descriptor.EngineDescriptor
import org.junit.platform.engine.support.descriptor.FilePosition
import org.junit.platform.engine.support.descriptor.FileSource
import org.junit.platform.engine.support.hierarchical.Node
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.forEachLine
import kotlin.io.path.isDirectory
import kotlin.io.path.name

object EngineDescriptorFactory {
    fun create(engineId: UniqueId, specFiles: List<Path>): EngineDescriptor {
        val engineDesc = EngineDescriptor(engineId, "FHIR Validator")

        specFiles.forEachIndexed { tsIndex, path ->
            val spec = loadConfig(path)
            val validator = FhirValidator.create(spec.validator)

            val testSuiteId = engineId.append<TestSuiteDescriptor>("$tsIndex")
            val testSuiteSource = FileSource.from(path.toFile())
            val testSuiteDesc = TestSuiteDescriptor(testSuiteId, spec.name ?: path.name, testSuiteSource)

            spec.tests.forEachIndexed { tcIndex, testCase ->
                val testCaseId = testSuiteId.append<TestCaseDescriptor>("$tcIndex")
                val testCaseSource = createFileSource(path, tcIndex)
                val testCaseDesc = TestCaseDescriptor(testCaseId, testCase, validator, testCaseSource)
                testSuiteDesc.addChild(testCaseDesc)
            }

            engineDesc.addChild(testSuiteDesc)
        }

        return engineDesc
    }
}

private inline fun <reified T> UniqueId.append(value: String) = append(T::class.simpleName, value)!!

/** Parsers needs to be explicitly mapped to file-extensions to work with ShadowJar. */
private val configLoader = ConfigLoader.Builder()
    .addFileExtensionMapping("json", JsonParser())
    .addFileExtensionMapping("yaml", YamlParser())
    .addFileExtensionMapping("yml", YamlParser())
    .build()

/** Loads the config/specification and resolves all relative paths. */
private fun loadConfig(specPath: Path): Specification {
    fun resolveAndNormalize(path: Path): Path {
        if (path.isAbsolute) return path
        val dir = if (specPath.isDirectory()) specPath else specPath.parent
        return dir.resolve(path).normalize()
    }

    val config = configLoader.loadConfigOrThrow<Specification>(specPath)

    // An IG can be specified as either package, file, folder or URL.
    // In case of file or folder we want the path to be resolved relative to the specification file.
    val resolvedIgs = config.validator.igs.map {
        try {
            resolveAndNormalize(Path(it)).toString()
        } catch (ex: Throwable) {
            it
        }
    }

    return config.copy(
        validator = config.validator.copy(igs = resolvedIgs),
        tests = config.tests.map {
            it.copy(source = resolveAndNormalize(it.source))
        }
    )
}

/** Creates a FileSource with FilePosition (line, column) of the 'source' property
 ** of the Test at [[testIndex]]. Works with both json and yaml files. */
private fun createFileSource(specPath: Path, testIndex: Int): FileSource {
    fun findAllMatches(pattern: Regex) =
        sequence<FilePosition> {
            val commentLinePattern = Regex("^ *#")
            var lineNr = 1
            specPath.forEachLine { line ->
                if (!commentLinePattern.matches(line)) {
                    pattern.findAll(line).forEach {
                        yield(FilePosition.from(lineNr, it.range.first + 1))
                    }
                }
                lineNr++
            }
        }

    val pattern = if (specPath.name.endsWith(".json")) "\"source\"" else "[ {]source: "
    val filePosition = findAllMatches(Regex(pattern)).elementAtOrNull(testIndex)
    return FileSource.from(specPath.toFile(), filePosition)
}

private class TestSuiteDescriptor(id: UniqueId, name: String, source: FileSource) :
    AbstractTestDescriptor(id, name, source), Node<FhirValidatorExecutionContext> {
    override fun getType() = TestDescriptor.Type.CONTAINER
}