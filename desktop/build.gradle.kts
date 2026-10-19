import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The game and the companion as desktop windows: the app's own UI and data code, the app's own
// native bridge (native/, built with CMake), and a small desktop frontend (src/main/kotlin/desktop).
plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.5.12"
}

val app = rootDir.resolve("../app/src/main/kotlin/com/pokedaisy/app")
// The same app sources ui-preview compiles (its shims stand in for what they use from Android).
val appIncludes = listOf(
    "companion/ui/**", "SettingsActivity.kt", "LibraryActivity.kt", "SavesLocation.kt",
    "CoverArtSync.kt", "CoverSync.kt", "DocsApi.kt", "DocsSearch.kt", "RomFolder.kt", "StorageAccess.kt", "SetupScreen.kt", "AppUpdater.kt", "AppUpdate.kt", "GameSaves.kt", "SaveBackups.kt", "GameInfo.kt", "LibraryOrder.kt", "RomSupportRequest.kt", "RomIntake.kt", "BestEffortShare.kt", "SaveStates.kt", "RomIdentity.kt", "RomArchive.kt", "CompanionSupport.kt", "CoverPicker.kt", "SteamGridDbGames.kt", "SteamGridDbClient.kt", "GameTitles.kt", "SaveEditorActivity.kt", "SaveFileEdits.kt", "FeatureFlags.kt",
    "ScreenScraperClient.kt", "ScreenScraperUi.kt", "GameMedia.kt", "LauncherScreen.kt", "WideScreen.kt",
    "achievements/**", "overlay/Overlays.kt", "overlay/OverlayLabels.kt",
)

val prepSrc = tasks.register<Sync>("prepSrc") {
    from(app) { appIncludes.forEach { include(it) } }
    from(rootDir.resolve("../app/src/test/kotlin/com/pokedaisy/app/companion/data/save")) { include("SyntheticSave.kt"); into("synthetic") }
    into(layout.buildDirectory.dir("appsrc"))
    filter { line: String ->
        line.replace("Icons.AutoMirrored.Filled.", "Icons.Filled.")
            .replace("icons.automirrored.filled.", "icons.filled.")
    }
}
sourceSets["main"].kotlin.srcDir("../ui-preview/src/shims")
tasks.withType<KotlinCompile>().configureEach {
    source(files(layout.buildDirectory.dir("appsrc")).builtBy(prepSrc))
    kotlinOptions.jvmTarget = "17"
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

dependencies {
    implementation(project(":data"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.json:json:20240303")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.12")
    implementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.5.12")
}

val nativeDir = rootDir.resolve("native/build")

fun JavaExec.desktopProps() {
    classpath = sourceSets["main"].runtimeClasspath
    // The shimmed AssetManager reads from here, like the APK's assets/.
    workingDir = rootDir.resolve("../app/src/main/assets")
    jvmArgs("-Djava.library.path=${nativeDir.absolutePath}")
    systemProperty("scratch", (project.findProperty("userdata")?.toString()) ?: layout.buildDirectory.dir("userdata").get().asFile.absolutePath)
    args = listOfNotNull(project.findProperty("rom")?.toString(), project.findProperty("sav")?.toString())
}

tasks.register<JavaExec>("run") {
    description = "Plays -Prom=PATH [-Psav=PATH]: the game window (EXPAND) and the companion window."
    mainClass.set("desktop.MainKt")
    desktopProps()
}

tasks.register<JavaExec>("selftest") {
    description = "Headless: boots -Prom [-Psav], runs a scripted session and writes game + companion PNGs to build/check."
    mainClass.set("desktop.CheckKt")
    desktopProps()
    systemProperty("java.awt.headless", "true")
    systemProperty("out", layout.buildDirectory.dir("check").get().asFile.absolutePath)
    systemProperty("script", project.findProperty("steps")?.toString().orEmpty())
}
