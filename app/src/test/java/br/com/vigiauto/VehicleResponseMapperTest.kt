package br.com.vigiauto

import br.com.vigiauto.data.lookup.VehicleResponseMapper
import br.com.vigiauto.domain.model.RestrictionStatus
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleResponseMapperTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun map(payload: String) =
        VehicleResponseMapper.map(json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject)

    @Test
    fun `mapeia campos planos e situacao negativa`() {
        val data = map(
            """
            {
              "placa": "EFR8092",
              "marca": "Chevrolet",
              "modelo": "Spin",
              "cor": "Prata",
              "anoFabricacao": "2019",
              "anoModelo": "2020",
              "situacao": "Nada consta"
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals("Chevrolet", data!!.brand)
        assertEquals("Spin", data.model)
        assertEquals(2019, data.manufactureYear)
        assertEquals(2020, data.modelYear)
        assertEquals(RestrictionStatus.NONE_IDENTIFIED, data.restrictionStatus)
        assertTrue(!data.incomplete)
    }

    @Test
    fun `mapeia campos aninhados e restricao positiva`() {
        val data = map(
            """
            {
              "veiculo": { "marca": "Fiat", "modelo": "Cronos", "cor": "Branco" },
              "restricoes": ["Roubo/Furto"]
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals("Fiat", data!!.brand)
        assertEquals(RestrictionStatus.CONFIRMED, data.restrictionStatus)
        assertEquals("Roubo/Furto", data.restrictionDescription)
    }

    @Test
    fun `resposta sem dados uteis e rejeitada`() {
        assertNull(map("""{"placa":"ABC1234"}"""))
    }

    @Test
    fun `dados parciais sao marcados como incompletos`() {
        val data = map("""{"marca":"Hyundai"}""")
        assertNotNull(data)
        assertEquals("Hyundai", data!!.brand)
        assertTrue(data.incomplete)
        assertEquals(RestrictionStatus.UNKNOWN, data.restrictionStatus)
    }

    @Test
    fun `status generico sem indicacao vira inconclusivo`() {
        val data = map("""{"marca":"Fiat","modelo":"Mobi","situacao":"Em análise"}""")
        assertNotNull(data)
        assertEquals(RestrictionStatus.INCONCLUSIVE, data!!.restrictionStatus)
    }

    @Test
    fun `ano embutido em texto e extraido`() {
        val data = map("""{"marca":"VW","modelo":"T-Cross","ano":"2021/2022"}""")
        assertNotNull(data)
        assertEquals(2021, data!!.manufactureYear)
    }

    @Test
    fun `mapeia schema real da API Consulta Placa`() {
        val data = map(
            """
            {
              "placa": "ABC1D23",
              "marca": "FIAT",
              "modelo": "CRONOS DRIVE 1.0",
              "ano": "2023",
              "anoModelo": "2023",
              "cor": "BRANCA",
              "municipio": "São Paulo",
              "uf": "SP",
              "situacao": "SEM RESTRICAO",
              "fipe": { "dados": [] },
              "extra": {
                "ano_fabricacao": "2023",
                "ano_modelo": "2023",
                "tipo_veiculo": "Automovel",
                "especie_veiculo": "Passageiro",
                "restricao_1": "SEM RESTRICAO",
                "restricao_2": "SEM RESTRICAO",
                "restricao_3": "SEM RESTRICAO",
                "restricao_4": "SEM RESTRICAO"
              }
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals("FIAT", data!!.brand)
        assertEquals("CRONOS DRIVE 1.0", data.model)
        assertEquals("BRANCA", data.color)
        assertEquals(2023, data.manufactureYear)
        assertEquals(2023, data.modelYear)
        assertEquals("Automovel", data.vehicleCategory)
        assertEquals("São Paulo", data.city)
        assertEquals("SP", data.state)
        assertEquals(RestrictionStatus.NONE_IDENTIFIED, data.restrictionStatus)
        assertEquals("Consulta Simples (API)", data.sourceName)
        assertTrue(!data.incomplete)
    }

    @Test
    fun `restricao positiva tem precedencia sobre sem restricao generico`() {
        val data = map(
            """
            {
              "marca": "FIAT",
              "modelo": "CRONOS",
              "situacao": "SEM RESTRICAO",
              "extra": {
                "restricao_1": "RESTRICAO ADMINISTRATIVA",
                "restricao_2": "SEM RESTRICAO"
              }
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals(RestrictionStatus.CONFIRMED, data!!.restrictionStatus)
        assertEquals("RESTRICAO ADMINISTRATIVA", data.restrictionDescription)
    }

    @Test
    fun `fonte padrao pode ser personalizada`() {
        val data = VehicleResponseMapper.map(
            json.parseToJsonElement("""{"marca":"Fiat","modelo":"Mobi"}""")
                as kotlinx.serialization.json.JsonObject,
            defaultSource = "Fonte de teste"
        )
        assertNotNull(data)
        assertEquals("Fonte de teste", data!!.sourceName)
    }

    @Test
    fun `roubado e furtado sao restricoes confirmadas`() {
        val roubado = map("""{"marca":"Fiat","modelo":"Mobi","situacao":"ROUBADO"}""")
        assertNotNull(roubado)
        assertEquals(RestrictionStatus.CONFIRMED, roubado!!.restrictionStatus)
        assertEquals("ROUBADO", roubado.restrictionDescription)

        val furtado = map("""{"marca":"Fiat","modelo":"Mobi","situacao":"FURTADO"}""")
        assertNotNull(furtado)
        assertEquals(RestrictionStatus.CONFIRMED, furtado!!.restrictionStatus)
        assertEquals("FURTADO", furtado.restrictionDescription)
    }

    @Test
    fun `booleano de roubo e reconhecido`() {
        val data = map("""{"marca":"Fiat","modelo":"Mobi","roubo":true,"furto":false}""")
        assertNotNull(data)
        assertEquals(RestrictionStatus.CONFIRMED, data!!.restrictionStatus)
        assertEquals("Roubo/Furto informado pela fonte", data.restrictionDescription)
    }

    @Test
    fun `schema oficial de historico de roubo e furto e reconhecido`() {
        val data = map(
            """
            {
              "status": "ok",
              "mensagem": "Consulta realizada com sucesso!",
              "dados": {
                "historico_roubo_furto": {
                  "registros_roubo_furto": {
                    "possui_registro": "sim",
                    "registros": [
                      {
                        "boletim_ocorrencia": "9996",
                        "data_boletim_ocorrencia": "02/09/2019",
                        "tipo_ocorrencia": "Declaração de Roubo",
                        "uf_ocorrencia": "PR"
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals(RestrictionStatus.CONFIRMED, data!!.restrictionStatus)
        assertTrue(data.restrictionDescription?.contains("Roubo", ignoreCase = true) == true)
    }

    @Test
    fun `historico sem registro nao vira restricao`() {
        val data = map(
            """
            {
              "status": "ok",
              "dados": {
                "historico_roubo_furto": {
                  "registros_roubo_furto": {
                    "possui_registro": "nao",
                    "registros": []
                  }
                }
              }
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals(RestrictionStatus.NONE_IDENTIFIED, data!!.restrictionStatus)
    }

    @Test
    fun `historico indisponivel vira inconclusivo`() {
        val data = map(
            """
            {
              "status": "ok",
              "dados": {
                "historico_roubo_furto": {
                  "registros_roubo_furto": {
                    "possui_registro": "indisponivel",
                    "registros": []
                  }
                }
              }
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals(RestrictionStatus.INCONCLUSIVE, data!!.restrictionStatus)
    }

    @Test
    fun `roubo tem precedencia sobre sem restricao`() {
        val data = map(
            """
            {
              "marca": "Fiat",
              "modelo": "Mobi",
              "situacao": "SEM RESTRICAO",
              "extra": { "restricao_1": "ROUBO" }
            }
            """.trimIndent()
        )
        assertNotNull(data)
        assertEquals(RestrictionStatus.CONFIRMED, data!!.restrictionStatus)
        assertEquals("ROUBO", data.restrictionDescription)
    }
}
