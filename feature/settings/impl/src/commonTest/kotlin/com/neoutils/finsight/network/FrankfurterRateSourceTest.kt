package com.neoutils.finsight.network

import com.neoutils.finsight.domain.repository.RemoteQuote
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The three shapes an answer can have, and the direction the request asks in. */
class FrankfurterRateSourceTest {

    private var lastRequest: HttpRequestData? = null

    private fun source(handler: MockRequestHandler) =
        FrankfurterRateSource(
            client = HttpClient(
                MockEngine { request ->
                    lastRequest = request
                    handler(request)
                }
            ) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            },
            baseUrl = "https://example.invalid",
        )

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

    /** A Sunday reading Friday's publication is stamped Friday, never today (design D5). */
    @Test
    fun `the date is the one the source declares`() = runTest {
        val source = source {
            respond(
                content = """{"amount":1.0,"base":"USD","date":"2026-07-31","rates":{"BRL":5.0583}}""",
                headers = jsonHeaders(),
            )
        }

        assertEquals(
            RemoteQuote.Observed(date = LocalDate(2026, 7, 31), rate = 5.0583),
            source.quote(currency = "USD", against = "BRL"),
        )
    }

    /** `base=<currency in use>&symbols=<base>` — the direction the row will be read in. */
    @Test
    fun `the request asks in the direction the row will be written`() = runTest {
        val source = source {
            respond(
                content = """{"amount":1.0,"base":"USD","date":"2026-07-31","rates":{"BRL":5.0583}}""",
                headers = jsonHeaders(),
            )
        }

        source.quote(currency = "USD", against = "BRL")

        val url = lastRequest!!.url
        assertEquals("USD", url.parameters["base"])
        assertEquals("BRL", url.parameters["symbols"])
        assertEquals("/v1/latest", url.encodedPath)
    }

    @Test
    fun `a code the source refuses is not covered`() = runTest {
        val source = source { respondError(HttpStatusCode.NotFound) }

        assertEquals(RemoteQuote.NotCovered, source.quote(currency = "XYZ", against = "BRL"))
    }

    /** A 200 that simply does not carry the symbol says the same thing. */
    @Test
    fun `a body without the symbol asked for is not covered`() = runTest {
        val source = source {
            respond(
                content = """{"amount":1.0,"base":"USD","date":"2026-07-31","rates":{}}""",
                headers = jsonHeaders(),
            )
        }

        assertEquals(RemoteQuote.NotCovered, source.quote(currency = "USD", against = "BRL"))
    }

    @Test
    fun `a server failure is unavailable`() = runTest {
        val source = source { respondError(HttpStatusCode.InternalServerError) }

        assertEquals(RemoteQuote.Unavailable, source.quote(currency = "USD", against = "BRL"))
    }

    @Test
    fun `an unreadable body is unavailable`() = runTest {
        val source = source {
            respond(content = """not json at all""", headers = jsonHeaders())
        }

        assertEquals(RemoteQuote.Unavailable, source.quote(currency = "USD", against = "BRL"))
    }
}
