import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import java.io.InputStream
import java.io.Writer
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Records what Compose Desktop packs into the distribution image, so a test can ask what the
 * user actually installs instead of what the build happens to have on hand.
 *
 * The source is `runtimeClasspath`, the configuration `createDistributable` copies into
 * `Finsight.app/Contents/app`: every jar there is one of these, under the same name plus the
 * content hash the packaging step appends. Each line is the artifact's origin and its jar,
 * separated by `|` — the origin because jars built from this repository are all named
 * `api-jvm.jar` or `impl-jvm.jar`, and only the component id says which module they are.
 */
abstract class WriteDistributionManifest : DefaultTask() {

    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:OutputFile
    abstract val manifest: RegularFileProperty

    @TaskAction
    fun write() {
        manifest.get().asFile.apply {
            parentFile.mkdirs()
            writeText(artifacts.get().sorted().joinToString(separator = "\n", postfix = "\n"))
        }
    }
}

/**
 * Launches the executable the user installs, hands it `--mcp`, and holds one conversation with it.
 *
 * The stdio mode is asserted from the inside by `McpStdioOverTheProtocolTest`, over pipes, in the
 * same process as the test. Four things are out of reach from there because they are properties of
 * the packaged program rather than of the code inside it, and this is where they are held true
 * (design D11):
 *
 * - the installed launcher speaks the protocol on the streams it was started with: `initialize`,
 *   then `tools/list`;
 * - `stderr` carries the opening line, which is all a client's log has to explain a session that
 *   went wrong with;
 * - `jpackage.app-path` is defined in the packaged process, and points at the launcher that was
 *   started. It is the property the settings section builds the client's command out of (design
 *   D9), it is in no `jpackage` man page, and this run is the only thing that keeps it true as the
 *   toolchain moves;
 * - no windowing toolkit starts: the process has no AWT thread and never loads `java.awt.Window`,
 *   so no window was opened and none could have been.
 *
 * **Run by hand, like the Maestro suite, and deliberately outside `jvmTest`.** It needs the image
 * `createDistributable` produces, which takes minutes of packaging that a test run has no reason to
 * pay for.
 *
 * **It calls no tool.** `initialize` and `tools/list` are answered without opening the database, so
 * the packaged process — which reads the real preferences and the real `~/.finance` of whoever runs
 * it, since those are the machine's and not the build's — is asked nothing that would write there.
 *
 * **What `tools/list` answers with is reported and not asserted.** The packaged process reads the
 * machine's own switch, and an empty list is the correct answer where the user has the server
 * switched off; the opening line on `stderr` says which of the two happened.
 */
abstract class VerifyMcpLauncher : DefaultTask() {

    /** The launcher inside the distribution image — the very file an installation puts on disk. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val launcher: RegularFileProperty

    /**
     * The argument that turns the executable into a server.
     *
     * Spelled here because a build script cannot see the app's own classes, and
     * `McpLaunchCommand.STDIO_ARGUMENT` is where the two ends of it agree. A spelling that drifted
     * from that one would not pass quietly: the launcher would take the other branch and open a
     * window, and nothing would ever answer on the standard output.
     */
    @get:Input
    abstract val stdioArgument: Property<String>

    /**
     * The JDK the running process is read with — the same one that packaged the image.
     *
     * `jcmd` asks the live process for its own system properties, its threads and whether a class
     * was ever loaded. It is the only way to learn those from outside without changing what is being
     * observed: an environment variable or a flag would answer about a process this task arranged
     * rather than about the one a client launches.
     */
    @get:Input
    abstract val javaHome: Property<String>

    /** What the run observed, in the same lines the console gets. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    init {
        // What it writes is a measurement of a program that was run, and a previous run's numbers
        // are not still true because nothing on disk has changed since.
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        val executable = launcher.get().asFile
        val jcmd = jcmd()

        val startedAt = System.nanoTime()
        val process = ProcessBuilder(executable.absolutePath, stdioArgument.get()).start()
        val diagnostics = CopyOnWriteArrayList<String>()
        val answers = LinkedBlockingQueue<String>()
        read(process.errorStream) { diagnostics.add(it) }
        read(process.inputStream) { answers.put(it) }
        val requests = process.outputStream.bufferedWriter()

        try {
            send(
                requests,
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to HANDSHAKE,
                    "method" to "initialize",
                    "params" to mapOf(
                        "protocolVersion" to PROTOCOL_VERSION,
                        "capabilities" to emptyMap<String, Any>(),
                        "clientInfo" to mapOf("name" to name, "version" to "1"),
                    ),
                ),
            )
            val handshake = answer(process, answers, HANDSHAKE)
            val secondsToHandshake = secondsSince(startedAt)
            val greeting = handshake["result"] as? Map<*, *>
            checkNotNull(greeting) { "the launcher refused the handshake: $handshake" }

            send(requests, mapOf("jsonrpc" to "2.0", "method" to "notifications/initialized"))
            send(
                requests,
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to LISTING,
                    "method" to "tools/list",
                    "params" to emptyMap<String, Any>(),
                ),
            )
            val listing = answer(process, answers, LISTING)
            val secondsToListing = secondsSince(startedAt)
            val tools = (listing["result"] as? Map<*, *>)?.get("tools") as? List<*>
            checkNotNull(tools) { "tools/list did not answer with a list of tools: $listing" }

            // Taken before the process is asked anything about itself: attaching to it loads
            // classes and starts a thread of its own, and what a session costs is the question.
            val memory = residentKilobytes(process.pid())

            // Asked while the conversation is open, because a process that has exited has no
            // properties and no threads left to be asked about.
            val properties = properties(jcmd, process.pid())
            val appPath = properties[PACKAGED_APP_PATH]
            check(!appPath.isNullOrBlank()) {
                "the packaged process defines no $PACKAGED_APP_PATH, which is the property the " +
                    "settings section builds the client's command out of (design D9)"
            }
            check(File(appPath).canonicalFile == executable.canonicalFile) {
                "$PACKAGED_APP_PATH is $appPath, and the launcher started was ${executable.canonicalPath}"
            }
            val appVersion = properties[PACKAGED_APP_VERSION]
            check(!appVersion.isNullOrBlank()) {
                "the packaged process defines no $PACKAGED_APP_VERSION, which the opening line " +
                    "reports the answering build with"
            }

            val opening = diagnostics.firstOrNull { line ->
                line.contains(appVersion) && line.contains("stdio") && line.contains("server")
            }
            checkNotNull(opening) {
                "stderr carried no opening line naming the version, the mode and the state of the " +
                    "server. What it carried:\n${diagnostics.joinToString(separator = "\n")}"
            }

            val toolkit = threads(jcmd, process.pid()).filter { it.startsWith("AWT-") || it.contains("AppKit") }
            check(toolkit.isEmpty()) { "a windowing toolkit is running in the stdio process: $toolkit" }
            val windows = ask(jcmd, process.pid(), "VM.class_hierarchy", WINDOW_CLASS)
                .lineSequence()
                .filter { it.contains(WINDOW_CLASS) }
                .toList()
            check(windows.isEmpty()) { "$WINDOW_CLASS was loaded by the stdio process: $windows" }

            // Closing the input is how a client ends a session, and the whole of how this process
            // is asked to stop.
            requests.close()
            check(process.waitFor(SHUTDOWN_SECONDS, TimeUnit.SECONDS)) {
                "the launcher did not exit within $SHUTDOWN_SECONDS s of its input being closed"
            }
            check(process.exitValue() == 0) { "the launcher exited with ${process.exitValue()}" }

            record(
                listOf(
                    "os = ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
                    "launcher = ${executable.canonicalPath}",
                    "$PACKAGED_APP_PATH = $appPath",
                    "$PACKAGED_APP_VERSION = $appVersion",
                    "opening line = $opening",
                    "seconds to initialize = ${seconds(secondsToHandshake)}",
                    "seconds to tools/list = ${seconds(secondsToListing)}",
                    "tools announced = ${tools.size}",
                    "resident memory = ${memory?.let { "$it KiB" } ?: "not reported by this platform"}",
                    "windowing toolkit = none: no AWT thread, and $WINDOW_CLASS never loaded",
                ),
            )
        } catch (failure: Throwable) {
            logger.error("what the launcher said on stderr:\n${diagnostics.joinToString(separator = "\n")}")
            throw failure
        } finally {
            // Whatever went wrong above, the process does not outlive the task.
            if (process.isAlive) {
                process.destroyForcibly().waitFor(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
            }
        }
    }

    /** Writes one JSON-RPC message, which the stdio transport reads as one line. */
    private fun send(requests: Writer, message: Map<String, Any>) {
        requests.write(JsonOutput.toJson(message))
        requests.write("\n")
        requests.flush()
    }

    /**
     * The answer to one request, skipping whatever the server says on its own along the way.
     *
     * It gives up early when the process is gone: a launcher that died has no answer coming, and
     * why it died is on `stderr`, which every failure of this task reports.
     */
    private fun answer(process: Process, answers: LinkedBlockingQueue<String>, id: Int): Map<*, *> {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ANSWER_SECONDS)
        while (System.nanoTime() < deadline) {
            val line = answers.poll(POLL_MILLISECONDS, TimeUnit.MILLISECONDS)
            if (line == null) {
                if (!process.isAlive && answers.isEmpty()) break
                continue
            }
            val message = JsonSlurper().parseText(line) as Map<*, *>
            if (message["id"] == id) return message
        }
        error("no answer to request $id from the launcher (alive: ${process.isAlive})")
    }

    /** The process's own system properties, as it holds them right now. */
    private fun properties(jcmd: File, pid: Long): Map<String, String> =
        ask(jcmd, pid, "VM.system_properties")
            .lineSequence()
            .filter { it.contains('=') && !it.startsWith('#') }
            .associate { line -> unescaped(line.substringBefore('=')) to unescaped(line.substringAfter('=')) }

    /**
     * A value as `java.util.Properties` writes it, where a separator and a backslash are escaped —
     * which is what a Windows path is made of.
     */
    private fun unescaped(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character == '\\' && index + 1 < value.length) {
                append(value[index + 1])
                index += 2
            } else {
                append(character)
                index += 1
            }
        }
    }

    /** The names of the threads the process is running. */
    private fun threads(jcmd: File, pid: Long): List<String> =
        ask(jcmd, pid, "Thread.print")
            .lineSequence()
            .filter { it.startsWith("\"") }
            .map { it.drop(1).substringBefore('"') }
            .toList()

    private fun ask(jcmd: File, pid: Long, vararg command: String): String {
        val probe = ProcessBuilder(listOf(jcmd.absolutePath, pid.toString()) + command)
            .redirectErrorStream(true)
            .start()
        val said = probe.inputStream.bufferedReader().readText()
        check(probe.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)) { "jcmd ${command.first()} did not answer" }
        check(probe.exitValue() == 0) { "jcmd ${command.first()} failed: $said" }
        return said
    }

    /** What the operating system says the process is holding, where it offers a way to ask. */
    private fun residentKilobytes(pid: Long): Long? = runCatching {
        val ps = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString()).start()
        val said = ps.inputStream.bufferedReader().readText().trim()
        ps.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)
        said.toLong()
    }.getOrNull()

    private fun jcmd(): File {
        val tools = File(javaHome.get(), "bin")
        return sequenceOf("jcmd", "jcmd.exe").map(tools::resolve).firstOrNull(File::canExecute)
            ?: error("no jcmd in $tools: the JDK that packaged the image is what the process is read with")
    }

    /** Reads a stream of the launcher to its end, on a thread of its own so neither can block. */
    private fun read(stream: InputStream, line: (String) -> Unit) {
        Thread { stream.bufferedReader().use { reader -> reader.lineSequence().forEach(line) } }
            .apply { isDaemon = true }
            .start()
    }

    private fun secondsSince(nanoTime: Long): Double = (System.nanoTime() - nanoTime) / 1_000_000_000.0

    /** A measurement reads the same wherever it is reported, so it is written in the one locale. */
    private fun seconds(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

    private fun record(observations: List<String>) {
        observations.forEach(logger::lifecycle)
        report.get().asFile.apply {
            parentFile.mkdirs()
            writeText(observations.joinToString(separator = "\n", postfix = "\n"))
        }
    }

    private companion object {

        /** The revision of the protocol the request opens with; the server answers with its own. */
        const val PROTOCOL_VERSION = "2025-06-18"

        const val HANDSHAKE = 1
        const val LISTING = 2

        const val PACKAGED_APP_PATH = "jpackage.app-path"
        const val PACKAGED_APP_VERSION = "jpackage.app-version"

        /** Every window of every toolkit on the JVM is one of these. */
        const val WINDOW_CLASS = "java.awt.Window"

        const val ANSWER_SECONDS = 60L
        const val SHUTDOWN_SECONDS = 30L
        const val PROBE_SECONDS = 60L
        const val POLL_MILLISECONDS = 200L
    }
}

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
    }
}

dependencies {
    implementation(projects.app.shared)
    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.koin.core)
    implementation(libs.multiplatform.settings)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.gitlive.firebase.app)
    implementation(libs.firebase.java.sdk)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.multiplatform.settings)
}

val writeDistributionManifest = tasks.register<WriteDistributionManifest>("writeDistributionManifest") {
    val runtimeClasspath = configurations.named("runtimeClasspath")
    dependsOn(runtimeClasspath)
    artifacts.set(
        runtimeClasspath
            .flatMap { it.incoming.artifacts.resolvedArtifacts }
            .map { resolved ->
                resolved.map { "${it.id.componentIdentifier.displayName}|${it.file.name}" }
            }
    )
    manifest.set(layout.buildDirectory.file("generated/distribution/distribution-classpath.txt"))
}

tasks.named<Copy>("processTestResources") {
    from(writeDistributionManifest)
}

// `./gradlew jvmTest` is the project's "all tests" command, and `kotlin("jvm")` names this
// module's test task `test`: without the alias the desktop suite — including the guard that
// the MCP server ships inside the distribution — sits outside the command that is supposed
// to run everything.
tasks.register("jvmTest") {
    dependsOn(tasks.named("test"))
}

tasks.register<VerifyMcpLauncher>("verifyMcpLauncher") {
    val distributable = tasks.named<AbstractJPackageTask>("createDistributable")
    val osName = providers.systemProperty("os.name")

    group = "verification"
    description = "Launches the packaged executable with --mcp and completes initialize -> " +
        "tools/list over stdio, checking the opening line on stderr, the jpackage.app-path of the " +
        "packaged process and that no window opens. Needs the image createDistributable builds, so " +
        "it is run by hand — like the Maestro suite — and is no part of jvmTest or check."

    dependsOn(distributable)
    launcher.set(
        distributable.flatMap { packaging ->
            packaging.destinationDir.file(
                packaging.packageName.zip(osName) { name, os ->
                    // Where the launcher sits inside the image, which is where an installation puts
                    // it: inside the bundle on macOS, at the root on Windows, under `bin` on Linux.
                    when {
                        os.startsWith("Mac") -> "$name.app/Contents/MacOS/$name"
                        os.startsWith("Windows") -> "$name/$name.exe"
                        else -> "$name/bin/$name"
                    }
                }
            )
        }
    )
    stdioArgument.set("--mcp")
    javaHome.set(distributable.flatMap { it.javaHome }.orElse(providers.systemProperty("java.home")))
    report.set(layout.buildDirectory.file("reports/mcp/verify-mcp-launcher.txt"))
}

compose.desktop {
    application {
        mainClass = "com.neoutils.finsight.MainKt"

        // ProGuard fails on ~9850 unresolved references pulled in by firebase-java-sdk,
        // okhttp and slf4j (android.*, conscrypt, slf4j.impl). Minification isn't worth
        // maintaining a -dontwarn list for a desktop app, so disable it.
        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)

            // ./gradlew :app:desktop:suggestRuntimeModules
            modules(
                "java.compiler",
                "java.instrument",
                "java.management",
                "java.naming",
                "java.prefs",
                "java.sql",
                "jdk.unsupported",
            )

            packageName = "Finsight"
            packageVersion = "1.10.0"
            description = "Finsight finance app"
            vendor = "NeoUtils"

            val icons = project.file("src/main/resources/icons")

            windows {
                iconFile.set(icons.resolve("icon.ico"))
                // Stable UUID required so Windows treats new installs as upgrades
                // of the same product. Never change this value.
                upgradeUuid = "8d0f5c2e-7b3a-4a1e-9f6c-2d4b6e8a0c11"
                menuGroup = "Finsight"
                perUserInstall = true
                dirChooser = true
                shortcut = true
            }

            macOS {
                iconFile.set(icons.resolve("icon.icns"))
                bundleID = "com.neoutils.finsight"
            }

            linux {
                iconFile.set(icons.resolve("icon.png"))
            }
        }
    }
}
