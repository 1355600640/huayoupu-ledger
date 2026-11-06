import java.security.MessageDigest

plugins { id("com.android.library"); kotlin("android") }

val ledgerAbi = providers.gradleProperty("ledgerAbi").getOrElse("arm64-v8a")
require(ledgerAbi in listOf("arm64-v8a", "x86_64")) { "Unsupported ledger ABI" }

android {
    namespace = "com.paddle.ocr"
    compileSdk = 36
    defaultConfig { minSdk = 30 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions { jvmTarget = "21" }
}

// Keep pinned originals intact; disable retained scratch arenas in a generated copy.
val generated = layout.buildDirectory.dir("generated/ledger-ort-memory")
val generatedAssets = layout.buildDirectory.dir("generated/ledger-ort-memory-assets")
val generateMemoryAdapter = tasks.register("generateLedgerOrtMemoryAdapter") {
    inputs.dir(layout.projectDirectory.dir("src/main/java"))
    outputs.dir(generated)
    outputs.dir(generatedAssets)
    doLast {
        val sourceRoot = layout.projectDirectory.dir("src/main/java").asFile
        val original = sourceRoot.resolve("com/paddle/ocr/engine/ORTSessionManager.kt")
        val source = original.readText()
        val needle = "            setIntraOpNumThreads(config.numThreads)"
        require(source.split(needle).size == 2) { "Pinned ORT session source changed; review adapter" }
        val adapted = source.replace(needle, needle + "\n            setCPUArenaAllocator(false) // Avoid retaining large scratch arenas.")
        sourceRoot.walkTopDown().filter { it.isFile && it.extension in listOf("kt", "java") }.forEach {
            val target = generated.get().asFile.resolve(it.relativeTo(sourceRoot))
            target.parentFile.mkdirs()
            if (it == original) target.writeText(adapted) else target.writeBytes(it.readBytes())
        }
        fun sha(text: String) = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val assets = generatedAssets.get().asFile
        assets.mkdirs()
        assets.resolve("paddle-runtime-policy.json").writeText("""{"cpuArena":false,"arenaShrink":false,"detectionOnlyNoArena":false,"detectionCpuArena":false,"recognitionCpuArena":false,"originalSourceSha256":"${sha(source)}","adaptedSourceSha256":"${sha(adapted)}","change":"setCPUArenaAllocator(false); all other source retained"}""")
        assets.resolve("licenses").mkdirs()
        assets.resolve("licenses/PaddleOCR-memory-adapter.txt").writeText("""
            ORTSessionManager derived from pinned PaddlePaddle/PaddleOCR Apache-2.0 source.
            Copyright (c) 2026 PaddlePaddle Authors. Original copyright and license retained.
            Ledger modification: setCPUArenaAllocator(false).
            Original SDK source remains unchanged; generated adapter replaces this class only.
        """.trimIndent() + "\n")
    }
}
android.sourceSets.getByName("main").java.setSrcDirs(listOf(generated))
kotlin.sourceSets.getByName("main").kotlin.setSrcDirs(listOf(generated))
android.sourceSets.getByName("main").assets.srcDir(generatedAssets)
tasks.matching { it.name.startsWith("compile") && it.name.endsWith("Kotlin") }.configureEach { dependsOn(generateMemoryAdapter) }
tasks.matching { (it.name.startsWith("merge") || it.name.startsWith("package")) && it.name.endsWith("Assets") }.configureEach { dependsOn(generateMemoryAdapter) }

dependencies {
    for (runtime in listOf("onnxruntime-reduced", "opencv-minimal")) {
        implementation(files(rootProject.file(".tools/ocr-native/$runtime-$ledgerAbi.aar").also {
            require(it.isFile) { "Run scripts/prepare-ledger-build.py before Gradle" }
        }))
    }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.16.0")
}
