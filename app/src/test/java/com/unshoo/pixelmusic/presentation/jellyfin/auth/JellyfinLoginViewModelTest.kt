package com.unshoo.pixelmusic.presentation.jellyfin.auth

import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.google.common.truth.Truth.assertThat
import com.unshoo.pixelmusic.MainCoroutineExtension
import com.unshoo.pixelmusic.data.jellyfin.JellyfinRepository
import com.unshoo.pixelmusic.data.jellyfin.model.JellyfinLibrary
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainCoroutineExtension::class)
class JellyfinLoginViewModelTest {

    private val repository = mockk<JellyfinRepository>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)

    private fun loginWithLibraries(libraries: List<JellyfinLibrary>): JellyfinLoginViewModel {
        coEvery { repository.login(any(), any(), any()) } returns Result.success("wolf")
        coEvery { repository.getLibraries() } returns Result.success(libraries)
        return JellyfinLoginViewModel(repository, workManager)
    }

    @Test
    fun `server with multiple music libraries asks for selection before syncing`() = runTest {
        val viewModel = loginWithLibraries(
            listOf(
                JellyfinLibrary(id = "a", name = "Lossless", collectionType = "music"),
                JellyfinLibrary(id = "b", name = "Lossy", collectionType = "music"),
                JellyfinLibrary(id = "c", name = "Movies", collectionType = "movies")
            )
        )

        viewModel.login("http://server", "wolf", "pw")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state).isInstanceOf(JellyfinLoginState.SelectLibraries::class.java)
        assertThat((state as JellyfinLoginState.SelectLibraries).libraries).hasSize(3)
        verify(exactly = 0) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `server with a single music library skips selection and starts sync`() = runTest {
        val viewModel = loginWithLibraries(
            listOf(
                JellyfinLibrary(id = "a", name = "Music", collectionType = "music"),
                JellyfinLibrary(id = "c", name = "Movies", collectionType = "movies")
            )
        )

        viewModel.login("http://server", "wolf", "pw")
        advanceUntilIdle()

        assertThat(viewModel.state.value)
            .isEqualTo(JellyfinLoginState.Success("wolf"))
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `library fetch failure falls back to syncing everything`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success("wolf")
        coEvery { repository.getLibraries() } returns Result.failure(Exception("boom"))
        val viewModel = JellyfinLoginViewModel(repository, workManager)

        viewModel.login("http://server", "wolf", "pw")
        advanceUntilIdle()

        assertThat(viewModel.state.value)
            .isEqualTo(JellyfinLoginState.Success("wolf"))
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `confirming a selection persists it and starts the first sync`() = runTest {
        val viewModel = loginWithLibraries(
            listOf(
                JellyfinLibrary(id = "a", name = "Lossless", collectionType = "music"),
                JellyfinLibrary(id = "b", name = "Lossy", collectionType = "music")
            )
        )
        viewModel.login("http://server", "wolf", "pw")
        advanceUntilIdle()

        viewModel.confirmLibrarySelection(setOf("a"))
        advanceUntilIdle()

        assertThat(viewModel.state.value)
            .isEqualTo(JellyfinLoginState.Success("wolf"))
        coVerify(exactly = 1) { repository.setSelectedLibraryIds(setOf("a")) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `skipping the selection keeps the default and still starts sync`() = runTest {
        val viewModel = loginWithLibraries(
            listOf(
                JellyfinLibrary(id = "a", name = "Lossless", collectionType = "music"),
                JellyfinLibrary(id = "b", name = "Lossy", collectionType = "music")
            )
        )
        viewModel.login("http://server", "wolf", "pw")
        advanceUntilIdle()

        viewModel.skipLibrarySelection()
        advanceUntilIdle()

        assertThat(viewModel.state.value)
            .isEqualTo(JellyfinLoginState.Success("wolf"))
        coVerify(exactly = 0) { repository.setSelectedLibraryIds(any()) }
        verify(exactly = 1) {
            workManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }
}
