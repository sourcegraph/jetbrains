rootProject.name = "Sourcegraph"

plugins {

    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

include(":jetbrains-shared")

project(":jetbrains-shared").projectDir = file("../jetbrains-shared")

val isCiServer = System.getenv().containsKey("CI")

buildCache { local { isEnabled = !isCiServer } }

