// PokeDaisy desktop: the app's own code (via ui-preview's data module and Android shims) with a
// desktop frontend. Maven Central / Gradle Plugin Portal only, like ui-preview.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "pokedaisy-desktop"
include(":data")
project(":data").projectDir = file("../ui-preview/data")
