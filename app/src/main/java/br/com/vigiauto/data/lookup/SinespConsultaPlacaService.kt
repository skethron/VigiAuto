package br.com.vigiauto.data.lookup

import br.com.vigiauto.domain.model.QueryStatus

/**
 * Contrato de uma fonte Sinesp.
 *
 * A implementação padrão é [SinespV1Source], que fala o protocolo v1 do
 * aplicativo oficial com o token pessoal do gov.br configurado pelo usuário.
 * O contrato também permite injetar outra fonte (por exemplo, um endpoint
 * autorizado) sem mudar a UI nem o repositório.
 */
interface SinespSource {
    suspend fun lookup(plate: String): LookupResult

    suspend fun testConnection(): ConnectionTestResult
}

/**
 * Ponto de integração do modo Sinesp.
 *
 * Sem uma fonte injetada, o modo retorna [QueryStatus.UNAVAILABLE] e não faz
 * nenhuma requisição. Em produção o contêiner do aplicativo injeta
 * [SinespV1Source]; se o token não estiver configurado, a própria fonte devolve a orientação
 * para configurá-lo, também sem consultar a rede.
 */
class SinespConsultaPlacaService(
    private val source: SinespSource? = null
) : ConsultaPlacaService {

    override val displayName: String = "SINESP Cidadão (v1)"
    override val isDemo: Boolean = false

    override suspend fun lookup(plate: String): LookupResult =
        source?.lookup(plate) ?: LookupResult.Failure(
            QueryStatus.UNAVAILABLE,
            "Nenhuma fonte Sinesp configurada. Nenhuma consulta foi feita."
        )

    override suspend fun testConnection(): ConnectionTestResult =
        source?.testConnection() ?: ConnectionTestResult(
            false,
            "Nenhuma fonte Sinesp configurada. Nenhuma requisição foi feita."
        )
}
