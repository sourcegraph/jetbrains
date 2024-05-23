import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.protocol.Repo
import com.sourcegraph.cody.context.ChatEnhancedContextStateProvider
import com.sourcegraph.cody.context.EnterpriseEnhancedContextStateController
import com.sourcegraph.cody.context.RemoteRepo
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.vcs.CodebaseName
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import junit.framework.TestCase
import org.junit.Test
import java.util.concurrent.CompletableFuture

class EnterpriseEnhancedContextStateControllerTest : TestCase() {
  private val mockProvider: ChatEnhancedContextStateProvider = mockk()
  private val initialState = EnhancedContextState()
  private val repos = listOf(Repo("repo1", "foo"), Repo("repo2", "bar"))
  private val remoteRepos = listOf(RemoteRepo("repo1"), RemoteRepo("repo2"))

  @MockK
  private companion object RemoteRepoUtilsMock {
    @MockK
    fun resolveReposWithErrorNotification(
      project: Project,
      repos: List<CodebaseName>,
      callback: (List<Repo>) -> Unit
    ): CompletableFuture<Unit> = mockk()
  }

  @BeforeEach
  fun setup() {
    every { mockProvider.getSavedState() } returns initialState
  }

  @Test
  fun testApplyRepoSpec() {
    val project: Project = mockk()
    val callback: (List<Repo>) -> Unit = mockk()

    every { RemoteRepoUtilsMock.resolveReposWithErrorNotification(project, any(), callback) } returns mockk()

    // Call the method under test
    val controller = EnterpriseEnhancedContextStateController(project, mockProvider)
    controller.updateRawSpec("repo1 repo2")

    // Verify the callback was called with the expected arguments
    verify { callback(match { it.size == 2 }) }
  }
}
