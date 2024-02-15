@file:Suppress("unused")

package com.sourcegraph.cody.autocomplete

import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.lang.reflect.Type
import junit.framework.TestCase

sealed class Animal(val type: String) {
  companion object {
    val deserializer: JsonDeserializer<Animal> =
        JsonDeserializer { element: JsonElement, _: Type, context: JsonDeserializationContext ->
          when (element.asJsonObject.get("type").asString) {
            "dog" -> context.deserialize<Dog>(element, Dog::class.java)
            "cat" -> context.deserialize<Cat>(element, Cat::class.java)
            else -> throw IllegalArgumentException("Unknown animal $element")
          }
        }
  }
}

data class Dog(val name: String, val age: Int) : Animal("dog")

data class Cat(val name: String, val age: String) : Animal("cat")

class SerdeTest : TestCase() {
  val gson =
      Gson().newBuilder().registerTypeAdapter(Animal::class.java, Animal.deserializer).create()

  fun assertRoundtrip(original: Any, expectedJsonString: String, clazz: Class<*>) {
    val obtainedJson = serialize(original, original.javaClass)
    assertEquals(expectedJsonString, obtainedJson)
    assertEquals(original, deserialize(obtainedJson, clazz))
  }

  fun <T> serialize(original: Any, clazz: Class<T>): String {
    return gson.toJson(original, clazz)
  }

  fun <T> deserialize(jsonString: String, clazz: Class<T>): T {
    return gson.fromJson(jsonString, clazz)
  }

  fun testSealedClass() {
    assertRoundtrip(Dog("Rex", 3), """{"name":"Rex","age":3,"type":"dog"}""", Animal::class.java)
    assertRoundtrip(
        Cat("Fluffy", "1 year"),
        """{"name":"Fluffy","age":"1 year","type":"cat"}""",
        Animal::class.java)
  }
}
