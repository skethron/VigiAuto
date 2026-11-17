package br.com.vigiauto.domain.plate

import br.com.vigiauto.domain.model.PlateCandidate

/**
 * Combina a leitura do quadro completo com a leitura do recorte ampliado.
 *
 * O recorte é mais confiável para detalhes finos (perna do Q, abertura do C,
 * barra do G), mas pode falhar se a caixa estiver errada. Por isso só aceitamos
 * uma leitura refinada quando ela é a mesma placa ou uma variante visualmente
 * equivalente. Uma leitura completamente diferente é descartada.
 */
object OcrCandidateMerger {

    fun merge(
        original: List<PlateCandidate>,
        refined: List<PlateCandidate>
    ): List<PlateCandidate> {
        if (original.isEmpty()) return refined.sortedByDescending { it.confidence }
        if (refined.isEmpty()) return original.sortedByDescending { it.confidence }

        val anchor = original.maxByOrNull { it.confidence } ?: return original
        val accepted = refined.filter { candidate ->
            candidate.normalized == anchor.normalized ||
                PlateNormalizer.isVisuallyEquivalent(candidate.normalized, anchor.normalized)
        }
        if (accepted.isEmpty()) return original.sortedByDescending { it.confidence }

        val best = accepted.maxWithOrNull(
            compareBy<PlateCandidate> { it.confidence }
                .thenBy { it.normalized == anchor.normalized }
        ) ?: return original

        val boosted = best.copy(
            // A leitura refinada vem de um recorte ampliado, com coordenadas
            // relativas a esse recorte. A caixa que vale para a fotografia é a
            // do candidato âncora, medida no quadro completo.
            box = anchor.box,
            lineCount = anchor.lineCount,
            confidence = maxOf(
                best.confidence + 0.10f,
                anchor.confidence + 0.06f
            ).coerceAtMost(0.99f)
        )

        // Substitui a hipótese antiga pela refinada. Assim o consenso e a
        // consulta passam a trabalhar com a leitura do recorte, sem manter as
        // duas variantes competindo como se fossem veículos diferentes.
        val remaining = original.filterNot { candidate ->
            candidate.normalized == boosted.normalized ||
                PlateNormalizer.isVisuallyEquivalent(candidate.normalized, boosted.normalized)
        }
        return (remaining + boosted).sortedByDescending { it.confidence }
    }

    fun bestPlate(candidates: List<PlateCandidate>): String? =
        candidates.maxByOrNull { it.confidence }?.normalized
}
