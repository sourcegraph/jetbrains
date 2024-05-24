package com.sourcegraph.cody.psi

object CodyPsiRangeProviderFactory {
    fun createProvider(language: String): CodyPsiRangeProvider? {
        return when (language) {
            "JAVA" -> JavaPsiRangeProvider()
            "KOTLIN" -> KotlinPsiRangeProvider()
            "GO" -> GoPsiRangeProvider()
            else -> null
        }
    }
}
