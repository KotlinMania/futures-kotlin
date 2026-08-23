import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import java.util.Properties

plugins {
    base
}

val parentProperties =
    Properties().also { properties ->
        val parentPropertiesFile = layout.projectDirectory.file("../gradle.properties").asFile
        if (parentPropertiesFile.isFile) {
            parentPropertiesFile.inputStream().use(properties::load)
        }
    }

fun propertyValue(
    name: String,
    default: String,
): String = providers.gradleProperty(name).orNull ?: parentProperties.getProperty(name) ?: default

val codeqlKotlinVersion = propertyValue("codeql.kotlin.version", "2.3.21")
val codeqlLanguageVersion =
    propertyValue(
        "kotlin.languageVersion",
        codeqlKotlinVersion.split('.').take(2).joinToString("."),
    )
val codeqlApiVersion = propertyValue("kotlin.apiVersion", codeqlLanguageVersion)
val jvmToolchainVersion = propertyValue("jvm.toolchain", "21")
val androidCompileSdk = propertyValue("android.compileSdk", "34")
val codeqlKotlinCommonSourceSetNames =
    propertyValue("project.codeql.kotlinCommonSourceSets", "commonMain").toSourceSetList()
val commonOptIns =
    listOf(
        "kotlin.time.ExperimentalTime",
        "kotlin.concurrent.atomics.ExperimentalAtomicApi",
        "kotlin.ExperimentalUnsignedTypes",
    )
val defaultCodeqlSourceClasspath =
    listOf(
        "org.jetbrains.kotlin:kotlin-stdlib:$codeqlKotlinVersion",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2",
    ).joinToString(",")

val codeqlKotlinc by configurations.creating
val codeqlSourceClasspath by configurations.creating
val codeqlAndroidAar by configurations.creating

dependencies {
    add("codeqlKotlinc", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$codeqlKotlinVersion")

    propertyValue("project.dependencies.codeqlSourceClasspath", defaultCodeqlSourceClasspath)
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { add("codeqlSourceClasspath", it) }

    propertyValue("project.dependencies.codeqlAndroidAar", "")
        .splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { add("codeqlAndroidAar", it) }
}

fun String.toSourceSetList(): List<String> =
    splitToSequence(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()

fun androidJar(): File {
    val candidateRoots =
        listOfNotNull(
            providers.environmentVariable("ANDROID_HOME").orNull,
            providers.environmentVariable("ANDROID_SDK_ROOT").orNull,
            layout.projectDirectory.dir("../.android-sdk").asFile.absolutePath,
        )
    val androidJar =
        candidateRoots
            .map { File(it).resolve("platforms/android-$androidCompileSdk/android.jar") }
            .firstOrNull { it.isFile }
    return requireNotNull(androidJar) {
        "Android CodeQL extraction requires platforms/android-$androidCompileSdk/android.jar under " +
            "ANDROID_HOME, ANDROID_SDK_ROOT, or ../.android-sdk"
    }
}

fun registerCodeqlCompileTask(
    taskName: String,
    sourceSetNames: List<String>,
    includeAndroidClasspath: Boolean,
) {
    tasks.register<JavaExec>(taskName) {
        description =
            "Compile ${sourceSetNames.joinToString(",")} Kotlin sources " +
                "with kotlinc $codeqlKotlinVersion for CodeQL Java/Kotlin extraction."
        group = "verification"
        classpath(codeqlKotlinc)
        mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

        val sourceRoot = layout.projectDirectory.dir("..")
        val outDir = layout.buildDirectory.dir("classes/kotlin/$taskName")
        val aarExtractDir = layout.buildDirectory.dir("codeql/android-aar/$taskName")
        val commonSources =
            files(
                codeqlKotlinCommonSourceSetNames.map { sourceSetName ->
                    fileTree(sourceRoot.dir("src/$sourceSetName/kotlin")) { include("**/*.kt") }
                },
            )
        val sources =
            files(
                sourceSetNames.map { sourceSetName ->
                    fileTree(sourceRoot.dir("src/$sourceSetName/kotlin")) { include("**/*.kt") }
                },
            )

        inputs.files(sources).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(commonSources).withPathSensitivity(PathSensitivity.RELATIVE)
        inputs.files(codeqlSourceClasspath).withNormalizer(ClasspathNormalizer::class.java)
        inputs.files(codeqlAndroidAar).withNormalizer(ClasspathNormalizer::class.java)
        outputs.dir(outDir)
        outputs.dir(aarExtractDir)

        // When no real Kotlin sources exist yet (early-stage port), generate a
        // minimal dummy source so CodeQL's Java/Kotlin extractor has something to
        // compile. Without this, the onlyIf guard skips the build task and CodeQL
        // fails with "didn't build any Java/Kotlin" — a fatal error.
        val dummySourceDir = layout.buildDirectory.dir("codeql-empty-source/$taskName")
        val dummyNamespace =
            propertyValue("project.namespace", "io.github.kotlinmania").let { ns ->
                ns.split(".").last()
            }
        val dummySourceFile =
            dummySourceDir
                .map { it.file("$dummyNamespace/codeql/_CodeqlEmptySource.kt") }

        onlyIf("CodeQL Kotlin extraction always runs (dummy source if needed)") {
            val commonSourceFiles = commonSources.files
            val sourceFiles = sources.files
            if (commonSourceFiles.isEmpty() || sourceFiles.isEmpty()) {
                logger.lifecycle(
                    "$taskName: no real Kotlin sources found for common source sets " +
                        "${codeqlKotlinCommonSourceSetNames.joinToString(",")} or target source sets " +
                        sourceSetNames.joinToString(",") + " — generating dummy source for CodeQL extraction",
                )
                val file = dummySourceFile.get().asFile
                file.parentFile.mkdirs()
                file.writeText(
                    "package $dummyNamespace.codeql\n\nprivate object _CodeqlEmptySource\n",
                )
            }
            true
        }

        doFirst {
            outDir.get().asFile.mkdirs()
            val extractedJars =
                codeqlAndroidAar.resolve().mapNotNull { aar ->
                    val extractTarget = aarExtractDir.get().asFile.resolve(aar.nameWithoutExtension)
                    extractTarget.mkdirs()
                    copy {
                        from(zipTree(aar))
                        include("classes.jar")
                        into(extractTarget)
                    }
                    extractTarget.resolve("classes.jar").takeIf { it.exists() }
                }
            val androidClasspath = if (includeAndroidClasspath) listOf(androidJar()) else emptyList()
            val fullClasspath =
                (codeqlSourceClasspath.resolve() + extractedJars + androidClasspath)
                    .joinToString(File.pathSeparator) { it.absolutePath }
            val commonSourceFiles = commonSources.files.toMutableList()
            val sourceFiles = sources.files.toMutableList()
            val stubProcMacro = dummySourceDir.get().file("io/github/kotlinmania/procmacro2/ProcMacroStub.kt").asFile
            stubProcMacro.parentFile.mkdirs()
            stubProcMacro.writeText(
                "package io.github.kotlinmania.procmacro2\n\n" +
                    "enum class Delimiter { Parenthesis, Brace, Bracket, None }\n" +
                    "enum class Spacing { Alone, Joint }\n" +
                    "class Span { companion object { fun callSite(): Span = Span() } }\n" +
                    "open class TokenTree\n" +
                    "class Group(val delimiter: Delimiter, val stream: TokenStream) : TokenTree()\n" +
                    "class Ident private constructor() : TokenTree() {\n" +
                    "    companion object { fun new(string: String, span: Span): Ident = Ident() }\n" +
                    "}\n" +
                    "class Literal private constructor() : TokenTree() {\n" +
                    "    companion object { fun string(string: String): Literal = Literal() }\n" +
                    "}\n" +
                    "class Punct(val asChar: Char, val spacing: Spacing) : TokenTree()\n" +
                    "class TokenStream {\n" +
                    "    companion object { fun new(): TokenStream = TokenStream() }\n" +
                    "    fun append(tree: TokenTree) {}\n" +
                    "    fun extendTokenStreams(streams: Iterable<TokenStream>) {}\n" +
                    "}\n",
            )
            commonSourceFiles.add(stubProcMacro)
            sourceFiles.add(stubProcMacro)

            val stubQuote = dummySourceDir.get().file("io/github/kotlinmania/quote/QuoteStub.kt").asFile
            stubQuote.parentFile.mkdirs()
            stubQuote.writeText(
                "package io.github.kotlinmania.quote\n\n" +
                    "import io.github.kotlinmania.procmacro2.TokenStream\n" +
                    "import io.github.kotlinmania.procmacro2.TokenTree\n\n" +
                    "interface ToTokens {\n" +
                    "    fun toTokens(tokens: TokenStream)\n" +
                    "}\n\n" +
                    "fun TokenStream.append(token: TokenTree) {}\n",
            )
            commonSourceFiles.add(stubQuote)
            sourceFiles.add(stubQuote)

            val stubSyn = dummySourceDir.get().file("io/github/kotlinmania/syn/SynStub.kt").asFile
            stubSyn.parentFile.mkdirs()
            stubSyn.writeText(
                "package io.github.kotlinmania.syn\n\n" +
                    "import io.github.kotlinmania.procmacro2.Punct\n" +
                    "import io.github.kotlinmania.procmacro2.TokenStream\n" +
                    "import io.github.kotlinmania.quote.ToTokens\n\n" +
                    "class SynError {\n" +
                    "    fun intoCompileError(): TokenStream = TokenStream.new()\n" +
                    "}\n\n" +
                    "sealed class SynResult<out T> {\n" +
                    "    companion object {\n" +
                    "        fun <T> success(value: T): SynResult<T> = Success(value)\n" +
                    "        fun <T> failure(error: SynError): SynResult<T> = Failure(error)\n" +
                    "    }\n" +
                    "    class Success<out T>(val value: T) : SynResult<T>()\n" +
                    "    class Failure<out T>(val error: SynError) : SynResult<T>()\n" +
                    "    inline fun getOrElse(onFailure: (SynError) -> @UnsafeVariance T): T =\n" +
                    "        when (this) {\n" +
                    "            is Success -> value\n" +
                    "            is Failure -> onFailure(error)\n" +
                    "        }\n" +
                    "}\n\n" +
                    "interface ParseStream {\n" +
                    "    fun isEmpty(): Boolean = true\n" +
                    "}\n\n" +
                    "interface Expr : ToTokens\n\n" +
                    "class ExprList : ToTokens {\n" +
                    "    fun toList(): List<Expr> = emptyList()\n" +
                    "    fun pushValue(value: Expr) {}\n" +
                    "    fun pushPunct(punct: Punct) {}\n" +
                    "    override fun toTokens(tokens: TokenStream) {}\n" +
                    "}\n\n" +
                    "fun parseExpr(input: ParseStream): SynResult<Expr> = SynResult.failure(SynError())\n\n" +
                    "object CommaParse {\n" +
                    "    fun parse(input: ParseStream): SynResult<Punct> = SynResult.failure(SynError())\n" +
                    "}\n\n" +
                    "fun <T> parse2(\n" +
                    "    parser: (ParseStream) -> SynResult<T>,\n" +
                    "    tokens: TokenStream,\n" +
                    "): SynResult<T> = SynResult.failure(SynError())\n",
            )
            commonSourceFiles.add(stubSyn)
            sourceFiles.add(stubSyn)

            // If no real sources were found, use the dummy source generated in onlyIf.
            if (commonSourceFiles.isEmpty()) {
                commonSourceFiles.add(dummySourceFile.get().asFile)
            }
            if (sourceFiles.isEmpty()) {
                sourceFiles.add(dummySourceFile.get().asFile)
            }
            args =
                listOf(
                    "-d",
                    outDir.get().asFile.absolutePath,
                    "-classpath",
                    fullClasspath,
                    "-jvm-target",
                    jvmToolchainVersion,
                    "-no-stdlib",
                    "-no-reflect",
                    "-language-version",
                    codeqlLanguageVersion,
                    "-api-version",
                    codeqlApiVersion,
                    "-Xmulti-platform",
                    "-Xcommon-sources=${commonSourceFiles.joinToString(",") { it.absolutePath }}",
                    "-Xexpect-actual-classes",
                ) + commonOptIns.flatMap { listOf("-opt-in", it) } + sourceFiles.map { it.absolutePath }
        }
    }
}

registerCodeqlCompileTask(
    "codeqlCompileJvm",
    propertyValue("project.codeql.kotlinSourceSets", "commonMain,jvmMain").toSourceSetList(),
    includeAndroidClasspath = false,
)

registerCodeqlCompileTask(
    "codeqlCompileAndroid",
    propertyValue("project.codeql.androidKotlinSourceSets", "commonMain,androidMain").toSourceSetList(),
    includeAndroidClasspath = true,
)
