package com.sourcegraph.cody.edit

import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocumentCodeTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    // TODO: Project setup?
  }

  fun testTopLevelFunction() {
    // Steps:
    // 1. Make a small kotlin file with a top level function
    //   - open it in the editor with CodeInsightTestFixture
    //   - use the markup language to specify the <caret> position
    // 2. Invoke the "Document Code" action on the function

    // TODO: Move this into testData as a file with <caret>
    // spotless:off
    myFixture.configureByText(
        "Foo.java",
        """
import java.util.*;

public class Foo {

    public void foo() {
        List<Integer> mystery = new ArrayList<>();
        mystery.add(0);
        mystery.add(1);
        for (int i = 2; i < 10; i++) {
          mystery.add(mystery.get(i - 1) + mystery.get(i - 2));
        }
        
        <caret>for (int i = 0; i < 10; i++) {
          System.out.println(mystery.get(i));
        }
    }
}
""") // spotless:on
    EditorTestUtil.executeAction(myFixture.editor, "cody.documentCodeAction")
  }

  fun testTopLevelClass() {
    assert(false)
    // ...
  }
}
