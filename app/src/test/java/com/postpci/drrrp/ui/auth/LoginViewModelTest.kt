package com.postpci.drrrp.ui.auth

import com.postpci.drrrp.data.auth.FakeAuthGateway
import com.postpci.drrrp.data.model.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeAuthGateway: FakeAuthGateway
    private lateinit var viewModel: LoginViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthGateway = FakeAuthGateway()
        viewModel = LoginViewModel(fakeAuthGateway)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `staff account attempting login on Patients tab is rejected`() = runTest {
        viewModel.onAudienceChange(LoginAudience.PATIENT)
        viewModel.onEmailChange(FakeAuthGateway.DEMO_STAFF_EMAIL)
        viewModel.onPasswordChange(FakeAuthGateway.DEMO_STAFF_PASSWORD)

        viewModel.submitSignIn()
        advanceUntilIdle()

        assertNull("Current user must stay null on role mismatch", fakeAuthGateway.currentUser.value)
        assertNotNull("Error message should be populated", viewModel.uiState.errorMessage)
        assertEquals(
            "This is a Clinical Staff account. Please switch to the 'Clinical Staff' tab to sign in.",
            viewModel.uiState.errorMessage,
        )
    }

    @Test
    fun `staff account logging in on Staff tab succeeds`() = runTest {
        viewModel.onAudienceChange(LoginAudience.STAFF)
        viewModel.onEmailChange(FakeAuthGateway.DEMO_STAFF_EMAIL)
        viewModel.onPasswordChange(FakeAuthGateway.DEMO_STAFF_PASSWORD)

        viewModel.submitSignIn()
        advanceUntilIdle()

        val user = fakeAuthGateway.currentUser.value
        assertNotNull("User should be logged in", user)
        assertEquals(UserRole.STAFF, user?.role)
        assertNull("Error message should be null", viewModel.uiState.errorMessage)
    }
}
