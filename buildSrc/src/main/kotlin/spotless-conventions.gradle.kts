plugins {
  id("com.diffplug.spotless")
}

spotless {
  lineEndings = com.diffplug.spotless.LineEnding.UNIX

  java {
    target("src/*/java/**/*.java")
    importOrder()
    removeUnusedImports()
    googleJavaFormat()
  }

  kotlinGradle {
    ktfmt()
    trimTrailingWhitespace()
  }

  kotlin {
    ktfmt()
    trimTrailingWhitespace()
    target("src/**/*.kt")
    toggleOffOn()
  }
}
