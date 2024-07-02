package com.sourcegraph.cody.util

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class TestFile(val value: String)
