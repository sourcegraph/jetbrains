package com.sourcegraph.cody.commands

enum class CommandId(val displayName: String, val source: String, val mnemonic: Char) {
  Explain("Explain Code", "explain", 'E'),
  Smell("Smell Code", "smell", 'S'),
  Test("Generate Test", "test", 'T')
}
