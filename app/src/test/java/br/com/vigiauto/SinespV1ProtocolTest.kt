package br.com.vigiauto

import br.com.vigiauto.data.lookup.SinespV1Protocol
import br.com.vigiauto.domain.model.RestrictionStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SinespV1ProtocolTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `assinatura confere com o vetor conhecido do provedor`() {
        assertEquals(
            "1700000000000.OjToZtQSJbVpGpeJ5R15FQrHnyj7adNz/u1zcXk1rHc=",
            SinespV1Protocol.appEnsurer(1700000000000L)
        )
    }

    @Test
    fun `token e normalizado sem Bearer e sem quebras de linha`() {
        assertEquals("abc.def", SinespV1Protocol.normalizeToken("  Bearer abc.\ndef  "))
        assertNull(SinespV1Protocol.normalizeToken("   "))
        assertNull(SinespV1Protocol.normalizeToken(null))
    }

    @Test
    fun `localizacao exige longitude e latitude validas`() {
        assertEquals(
            "-47.8825,-15.7942",
            SinespV1Protocol.normalizeGeo(" -47.8825 , -15.7942 ")
        )
        assertNull(SinespV1Protocol.normalizeGeo("200,-15"))
        assertNull(SinespV1Protocol.normalizeGeo("-47.8825"))
        assertNull(SinespV1Protocol.normalizeGeo("abc,def"))
    }

    @Test
    fun `extrai code somente do redirect https do sinesp`() {
        assertEquals(
            "abc123",
            SinespV1Protocol.extractCodeFromRedirect(
                "https://cidadao2.sinesp.gov.br/api/govbr/redirect?code=abc123&state=xyz"
            )
        )
        assertEquals(
            "a+b/c",
            SinespV1Protocol.extractCodeFromRedirect(
                "https://cidadao2.sinesp.gov.br/api/govbr/redirect?code=a%2Bb%2Fc"
            )
        )
        assertNull(
            SinespV1Protocol.extractCodeFromRedirect(
                "http://cidadao2.sinesp.gov.br/api/govbr/redirect?code=abc123"
            )
        )
        assertNull(
            SinespV1Protocol.extractCodeFromRedirect(
                "https://exemplo.invalido/api/govbr/redirect?code=abc123"
            )
        )
        assertNull(
            SinespV1Protocol.extractCodeFromRedirect(
                "https://cidadao2.sinesp.gov.br/outro?code=abc123"
            )
        )
        assertNull(
            SinespV1Protocol.extractCodeFromRedirect(
                "https://cidadao2.sinesp.gov.br/api/govbr/redirect?state=xyz"
            )
        )
    }

    @Test
    fun `aceita code ou url de retorno colados manualmente`() {
        assertEquals(
            "abc123",
            SinespV1Protocol.extractCode(
                "https://cidadao2.sinesp.gov.br/api/govbr/redirect?code=abc123&state=xyz"
            )
        )
        assertEquals("code-manual", SinespV1Protocol.extractCode("  code-manual  "))
        assertNull(SinespV1Protocol.extractCode("https://exemplo/redirect?state=xyz"))
        assertNull(SinespV1Protocol.extractCode("texto com espaços"))
    }

    @Test
    fun `extrai expiracao de jwt e ignora token opaco`() {
        val payload = java.util.Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("""{"exp":1700000000}""".toByteArray(Charsets.UTF_8))
        assertEquals(
            1_700_000_000_000L,
            SinespV1Protocol.tokenExpiryEpochMs("header.$payload.signature")
        )
        assertNull(SinespV1Protocol.tokenExpiryEpochMs("token-opaco"))
    }

    @Test
    fun `timestamp usa UTC com milissegundos`() {
        assertEquals(
            "2023-11-14T22:13:20.000Z",
            SinespV1Protocol.geoTimestamp(1700000000000L)
        )
    }

    @Test
    fun `cabecalhos usam token cru e localizacao normalizada`() {
        val headers = SinespV1Protocol.headers(
            token = "Bearer token-de-teste",
            uniqueId = "unique-1",
            geo = " -47.8825 , -15.7942 ",
            timestampMs = 1700000000000L
        )

        assertEquals("token-de-teste", headers["Authorization"])
        assertEquals("unique-1", headers["UniqueId"])
        assertEquals("-47.8825,-15.7942", headers["Geolocation"])
        assertEquals("2023-11-14T22:13:20.000Z", headers["GeolocationTimestamp"])
        assertEquals("100", headers["GeolocationAccuracy"])
        assertEquals(
            SinespV1Protocol.appEnsurer(1700000000000L),
            headers["AppEnsurer"]
        )
    }

    @Test
    fun `resposta normal vira sem restricao identificada`() {
        val data = map(
            """
            {
              "plate": "ABC1D23",
              "model": "FIAT/CRONOS DRIVE 1.0",
              "year": "2023",
              "fabricationYear": "2022",
              "color": "BRANCA",
              "city": "SAO PAULO",
              "state": "SP",
              "chassis": "***1234",
              "date": "2026-10-20",
              "alert": [],
              "hasCortexValidation": true
            }
            """
        )

        assertNotNull(data)
        assertEquals("FIAT", data!!.brand)
        assertEquals("CRONOS DRIVE 1.0", data.model)
        assertEquals("BRANCA", data.color)
        assertEquals(2022, data.manufactureYear)
        assertEquals(2023, data.modelYear)
        assertEquals("SAO PAULO", data.city)
        assertEquals("SP", data.state)
        assertEquals(RestrictionStatus.NONE_IDENTIFIED, data.restrictionStatus)
        assertTrue(data.restrictionDescription!!.contains("Sem registro", ignoreCase = true))
        assertEquals(SinespV1Protocol.SOURCE_NAME, data.sourceName)
        assertEquals(false, data.incomplete)
    }

    @Test
    fun `roubo confirmado inclui SENATRAN e data da ocorrencia`() {
        val data = map(
            """
            {
              "plate": "ABC1D23",
              "model": "HONDA/CB 300R",
              "year": "2010",
              "fabricationYear": "2010",
              "color": "PRETA",
              "alert": [{"type": "ROUBADO", "occurrenceDate": "2025-01-02T00:00:00"}],
              "hasCortexValidation": false
            }
            """
        )

        assertNotNull(data)
        assertEquals(RestrictionStatus.CONFIRMED, data!!.restrictionStatus)
        assertTrue(data.restrictionDescription!!.contains("roubado/furtado", ignoreCase = true))
        assertTrue(data.restrictionDescription!!.contains("SENATRAN"))
        assertTrue(data.restrictionDescription!!.contains("Ocorrência: 02-01-2025"))
        assertTrue(!data.restrictionDescription!!.contains("2025-01-02"))
    }

    @Test
    fun `roubo com Cortex validado nao recebe sufixo SENATRAN`() {
        val data = map(
            """
            {
              "plate": "ABC1D23",
              "model": "HONDA/CB 300R",
              "alert": [{"type": "FURTADO"}],
              "hasCortexValidation": true
            }
            """
        )

        assertNotNull(data)
        assertEquals(RestrictionStatus.CONFIRMED, data!!.restrictionStatus)
        assertTrue(data.restrictionDescription!!.contains("roubado/furtado", ignoreCase = true))
        assertTrue(!data.restrictionDescription!!.contains("SENATRAN"))
    }

    @Test
    fun `alerta desconhecido fica inconclusivo em vez de sem restricao`() {
        val data = map(
            """
            {
              "plate": "ABC1D23",
              "model": "FIAT/CRONOS",
              "alert": [{"type": "RESTRICAO_ADMINISTRATIVA"}],
              "hasCortexValidation": true
            }
            """
        )

        assertNotNull(data)
        assertEquals(RestrictionStatus.INCONCLUSIVE, data!!.restrictionStatus)
        assertTrue(data.restrictionDescription!!.contains("RESTRICAO_ADMINISTRATIVA"))
    }

    @Test
    fun `alerta vazio com Cortex falso recebe sufixo SENATRAN`() {
        val data = map(
            """
            {
              "plate": "ABC1D23",
              "model": "FIAT/CRONOS",
              "alert": [],
              "hasCortexValidation": false
            }
            """
        )

        assertNotNull(data)
        assertEquals(RestrictionStatus.NONE_IDENTIFIED, data!!.restrictionStatus)
        assertTrue(data.restrictionDescription!!.contains("SENATRAN"))
    }

    @Test
    fun `campo alert ausente nao vira sem restricao`() {
        val data = map(
            """
            {
              "plate": "ABC1D23",
              "model": "FIAT/CRONOS",
              "hasCortexValidation": true
            }
            """
        )

        assertNotNull(data)
        assertEquals(RestrictionStatus.INCONCLUSIVE, data!!.restrictionStatus)
        assertTrue(data.restrictionDescription!!.contains("não informou", ignoreCase = true))
    }

    @Test
    fun `resposta envelopada em data e aceita`() {
        val data = map(
            """
            {
              "data": {
                "plate": "ABC1D23",
                "model": "VW/GOL 1.0",
                "year": "2020",
                "color": "PRATA",
                "alert": [],
                "hasCortexValidation": true
              }
            }
            """
        )

        assertNotNull(data)
        assertEquals("VW", data!!.brand)
        assertEquals("GOL 1.0", data.model)
        assertEquals(2020, data.modelYear)
    }

    @Test
    fun `resposta sem campos esperados e rejeitada`() {
        assertNull(map("""{"message":"ok","status":200}"""))
    }

    private fun map(raw: String) = SinespV1Protocol.mapResponse(
        Json.parseToJsonElement(raw) as JsonObject
    )
}
