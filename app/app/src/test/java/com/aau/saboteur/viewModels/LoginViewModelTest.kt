package com.aau.saboteur.viewModels

import com.aau.saboteur.data.repository.AuthRepository
import com.aau.saboteur.model.User
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val repository = mockk<AuthRepository>()
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = LoginViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `successful login should invoke onSuccess and clear loading state`() = runTest {
        val expectedUser = User(id = 1L, username = "testuser", playerId = "PID-1")
        coEvery { repository.loginUser("testuser", "pw") } returns Result.success(expectedUser)

        var captured: User? = null
        viewModel.login("testuser", "pw") { captured = it }

        assertEquals(expectedUser, captured)
        assertFalse(viewModel.isLoading)
        assertNull(viewModel.errorMessage)
    }

    @Test
    fun `login with IOException should set connection_failed error`() = runTest {
        coEvery { repository.loginUser(any(), any()) } returns Result.failure(IOException("no network"))

        viewModel.login("user", "pw") { }

        assertEquals("error.connection_failed", viewModel.errorMessage)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `login with other exception should set login_failed error`() = runTest {
        coEvery { repository.loginUser(any(), any()) } returns Result.failure(IllegalStateException("bad creds"))

        viewModel.login("user", "pw") { }

        assertEquals("error.login_failed", viewModel.errorMessage)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun `isLoading should be true while the suspending call is in flight`() {
        // Use StandardTestDispatcher so the suspending call doesn't complete immediately
        val standardDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(standardDispatcher)
        val vm = LoginViewModel(repository)

        coEvery { repository.loginUser(any(), any()) } coAnswers {
            delay(1_000)
            Result.success(User(id = 1L, username = "u"))
        }

        vm.login("u", "pw") { }
        standardDispatcher.scheduler.advanceTimeBy(500)
        assertTrue(vm.isLoading)

        standardDispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.isLoading)
    }

    @Test
    fun `successful login with null password should still invoke onSuccess`() = runTest {
        val user = User(id = 2L, username = "guest", playerId = "GP")
        coEvery { repository.loginUser("guest", null) } returns Result.success(user)

        var captured: User? = null
        viewModel.login("guest", null) { captured = it }

        assertEquals(user, captured)
    }
}
