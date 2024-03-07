package utils

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.sourcegraph.utils.CodyFormatter
import junit.framework.TestCase

class CodyFormatterTest : BasePlatformTestCase() {
  private val testFileContent =
      """|
         |public class HelloWorld {
         |    public static void main(String[] args) {
         |        System.out.println("Hello World!");
         |        // MAIN
         |    }
         |    // CLASS
         |}"""
          .trimMargin()

  private var insideMainOffset = testFileContent.indexOf("// MAIN")
  private var insideClassOffset = testFileContent.indexOf("// CLASS")

  private fun formatText(text: String, offset: Int): String {
    val psiFactory = PsiFileFactory.getInstance(project)
    val psiFile =
        psiFactory.createFileFromText("FORMATTING_TEST", JavaFileType.INSTANCE, testFileContent)
    return CodyFormatter.formatStringBasedOnDocument(
        text, myFixture.project, psiFile.viewProvider.document, offset)
  }

  fun `test single line formatting`() {
    TestCase.assertEquals("int x = 2;", formatText("int   x =   2;", insideMainOffset))
  }

  fun `test single line formatting to multiline`() {
    TestCase.assertEquals(
        """|public static int fib(int n) {
           |        if (n <= 1) {
           |            return n;
           |        }
           |        return fib(n - 1) + fib(n - 2);
           |    }"""
            .trimMargin(),
        formatText(
            "public static int fib(int n) { if (n <= 1) { return n; } return fib(n-1) + fib(n-2);  }",
            insideClassOffset))
  }
}
