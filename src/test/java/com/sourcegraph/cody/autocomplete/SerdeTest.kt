@file:Suppress("unused")

package com.sourcegraph.cody.autocomplete

import com.google.gson.Gson
import junit.framework.TestCase

sealed class Animal

data class Dog(val name: String, val age: Int) : Animal()

data class Cat(val name: String, val age: String) : Animal()

class SerdeTest : TestCase() {
  fun assertRoundtrip(original: Any, jsonString: String, clazz: Class<*>) {
    assertEquals(jsonString, serialize(original, clazz))
    assertEquals(original, deserialize(serialize(original, clazz), clazz))
  }

  fun <T> serialize(original: Any, clazz: Class<T>): String {
    return Gson().toJson(original, clazz)
  }

  fun <T> deserialize(jsonString: String, clazz: Class<T>): T {
    return Gson().fromJson(jsonString, clazz)
  }

  fun testSealedClass() {
    assertRoundtrip(Dog("Rex", 3), """{"name":"Rex","age":3}""", Animal::class.java)
    assertRoundtrip(
        Cat("Fluffy", "1 year"), """{"name":"Fluffy","age":"1 year"}""", Animal::class.java)
  }
}
