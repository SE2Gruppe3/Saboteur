package com.aau.se2game.network

import com.aau.se2game.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkClientTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        NetworkConstants.baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        // Reset to original value to avoid side effects on other tests
        NetworkConstants.baseUrl = BuildConfig.BASE_URL
    }

    @Test
    fun `test NetworkConstants pingEndpoint`() {
        NetworkConstants.baseUrl = "http://test.com"
        assertEquals("http://test.com/api/ping", NetworkConstants.pingEndpoint)
    }

    @Test
    fun `test runConnectionTest returns success for 200 OK`() = runBlocking {
        val responseBody = "pong"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(responseBody)
        )

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Success: HTTP 200"))
        assertTrue(result.contains(responseBody))
    }

    @Test
    fun `test runConnectionTest returns success for 204 No Content`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Success: HTTP 204"))
    }

    @Test
    fun `test runConnectionTest returns failed for 404 Not Found`() = runBlocking {
        val errorBody = "Not Found"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody(errorBody)
        )

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Failed: HTTP 404"))
        assertTrue(result.contains(errorBody))
    }

    @Test
    fun `test runConnectionTest returns failed for 400 Bad Request with error body`() = runBlocking {
        val errorBody = "Bad request details"
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(errorBody)
        )

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Failed: HTTP 400"))
        assertTrue(result.contains(errorBody))
    }

    @Test
    fun `test runConnectionTest returns failed for 500 Internal Server Error`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Failed: HTTP 500"))
    }

    @Test
    fun `test runConnectionTest handles null body reader gracefully`() = runBlocking {
        // Mocking a response where the stream might be empty/null 
        // MockWebServer usually provides a stream, but we ensure the .orEmpty() logic is covered
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("")
        )

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Success: HTTP 200"))
    }

    @Test
    fun `test runConnectionTest returns connection error on exception`() = runBlocking {
        // Shutdown server to trigger a connection exception
        mockWebServer.shutdown()

        val result = NetworkClient.runConnectionTest()

        assertTrue(result.contains("Connection error"))
    }
}
