/*
import com.intellij.openapi.project.Project
import com.sourcegraph.cody.agent.protocol.Repo
import com.sourcegraph.cody.context.ChatEnhancedContextStateProvider
import com.sourcegraph.cody.context.EnterpriseEnhancedContextStateController
import com.sourcegraph.cody.context.RemoteRepo
import com.sourcegraph.cody.context.RemoteRepoUtils
import com.sourcegraph.cody.history.state.EnhancedContextState
import com.sourcegraph.vcs.CodebaseName
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import junit.framework.TestCase
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class EnterpriseEnhancedContextStateControllerTest : TestCase() {
  private val provider: ChatEnhancedContextStateProvider = mockk()
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
    every { provider.getSavedState() } returns initialState
    every { provider.updateAgentState(captureLambda()) } answers {
      lambda<(EnhancedContextState) -> EnhancedContextState>().invoke(initialState)
    }
  }

  @Test
  fun testApplyRepoSpec() {
    val project: Project = mockk()

    mockkObject(RemoteRepoUtils)
    every { RemoteRepoUtils.resolveReposWithErrorNotification(project, any(), captureLambda()) } answers {
      lambda<(List<Repo>) -> Unit>().invoke(listOf(
        Repo("repo1", "foo"),
        Repo("repo2", "bar"),
      ))
      CompletableFuture<Unit>().completeOnTimeout(null, 100, TimeUnit.MILLISECONDS)
    }

    // Call the method under test
    val controller = EnterpriseEnhancedContextStateController(project, provider)
    controller.updateRawSpec("repo1 repo2")

    // Verify the callback was called with the expected arguments
    verify {
      provider.updateAgentState(listOf(Repo("repo1", "foo"), Repo("repo2", "bar")))
    }
  }
}
*/
// TODO, tests but there's too much thread hopping and too many callbacks