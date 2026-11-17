package br.com.vigiauto.domain.plate

/**
 * Normaliza textos de OCR para os dois padrões brasileiros:
 * - antigo: LLLDDDD
 * - Mercosul: LLLDLDD
 *
 * Correções posicionais só são aplicadas quando o caractere está em uma posição
 * incompatível e existe um mapeamento visual inequívoco (O/0, I/1, B/8 etc.).
 * Quando a leitura já forma uma placa válida, ela é preservada. Hipóteses
 * conflitantes entre antigo/Mercosul e U/V são expostas por [alternatives] e só
 * usadas como fallback depois de uma consulta sem resultado.
 */
object PlateNormalizer {

    private const val MAX_CORRECTIONS = 2
    private const val MAX_ALTERNATIVES = 8

    private val digitToLetter = mapOf(
        '0' to 'O', '1' to 'I', '2' to 'Z', '3' to 'E', '4' to 'A',
        '5' to 'S', '6' to 'G', '7' to 'T', '8' to 'B'
    )

    private val letterToDigit = mapOf(
        'O' to '0', 'I' to '1', 'Z' to '2', 'E' to '3', 'A' to '4',
        'S' to '5', 'G' to '6', 'T' to '7', 'B' to '8'
    )

    /**
     * Confusões que podem gerar duas placas válidas diferentes. O padrão
     * LLLDLDD (Mercosul) e o LLLDDDD (antigo) se sobrepõem na quinta posição,
     * então I/1 e O/0 não podem ser resolvidos apenas pela posição. U/V são
     * ambas letras válidas em qualquer posição de letra.
     *
     * Q/O e C/G são o caso mais traiçoeiro: as duas leituras são placas
     * válidas e a API pode até devolver outro veículo para a hipótese errada.
     * Por isso elas também entram nas alternativas e na equivalência visual.
     */
    private val visualConfusions: Map<Char, List<Char>> = mapOf(
        'I' to listOf('1'),
        '1' to listOf('I'),
        'O' to listOf('0', 'Q', 'C', 'G'),
        '0' to listOf('O'),
        'Q' to listOf('O'),
        'C' to listOf('G', 'O'),
        'G' to listOf('C', 'O'),
        'U' to listOf('V'),
        'V' to listOf('U')
    )

    /**
     * Peso de cada troca. O OCR normalmente perde a perna do Q (Q → O) e a
     * barra do G (G → C), mas também pode fechar o C (C → O). A ordem das
     * alternativas prioriza as trocas mais prováveis.
     */
    private val confusionWeights: Map<Pair<Char, Char>, Float> = mapOf(
        ('O' to 'Q') to 1.00f,
        ('Q' to 'O') to 0.75f,
        ('C' to 'G') to 0.95f,
        ('G' to 'C') to 0.75f,
        ('C' to 'O') to 0.70f,
        ('O' to 'C') to 0.65f,
        ('G' to 'O') to 0.55f,
        ('O' to 'G') to 0.50f,
        ('O' to '0') to 0.80f,
        ('0' to 'O') to 0.80f,
        ('I' to '1') to 0.80f,
        ('1' to 'I') to 0.80f,
        ('U' to 'V') to 0.65f,
        ('V' to 'U') to 0.65f
    )

    fun clean(raw: String): String =
        raw.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun normalize(raw: String): String? {
        val value = clean(raw)
        if (value.length != 7) return null
        if (PlatePattern.isPlate(value)) return value

        val mercosul = tryCorrect(value, letterPositions = setOf(0, 1, 2, 4), digitPositions = setOf(3, 5, 6))
        val old = tryCorrect(value, letterPositions = setOf(0, 1, 2), digitPositions = setOf(3, 4, 5, 6))

        return when {
            mercosul != null && old != null -> when {
                mercosul.second < old.second -> mercosul.first
                old.second < mercosul.second -> old.first
                else -> null
            }
            mercosul != null -> mercosul.first
            old != null -> old.first
            else -> null
        }
    }

    /**
     * Placas alternativas plausíveis para uma leitura ambígua.
     *
     * A lista é usada como fallback quando a consulta da placa principal não
     * encontra o veículo. Cada alternativa troca um ou dois caracteres
     * visualmente confundíveis, mantendo o padrão brasileiro válido. A ordem
     * prioriza Q/O e C/G, que são as confusões que produzem outra placa
     * aparentemente válida.
     */
    fun alternatives(normalized: String): List<String> {
        val primary = normalize(normalized) ?: return emptyList()
        val positions = primary.indices.filter { visualConfusions[primary[it]].orEmpty().isNotEmpty() }
        if (positions.isEmpty()) return emptyList()

        data class Ranked(val score: Float, val swaps: Int, val oZeroFifth: Boolean)

        val ranked = LinkedHashMap<String, Ranked>()

        fun addCombination(
            indexes: List<Int>,
            depth: Int = 0,
            chars: CharArray = primary.toCharArray(),
            score: Float = 0f
        ) {
            if (depth == indexes.size) {
                val candidate = String(chars)
                if (candidate != primary && PlatePattern.isPlate(candidate)) {
                    val entry = Ranked(
                        score = score,
                        swaps = indexes.size,
                        oZeroFifth = isFifthOZeroSwap(primary, candidate)
                    )
                    val previous = ranked[candidate]
                    if (previous == null || entry.score > previous.score ||
                        (entry.score == previous.score && entry.swaps < previous.swaps)
                    ) {
                        ranked[candidate] = entry
                    }
                }
                return
            }

            val index = indexes[depth]
            val original = primary[index]
            for (replacement in visualConfusions.getValue(original)) {
                chars[index] = replacement
                val weight = confusionWeights[original to replacement] ?: 0.40f
                addCombination(indexes, depth + 1, chars, score + weight)
            }
            chars[index] = original
        }

        // Uma troca cobre o caso comum. Duas trocas cobrem leituras com Q/O
        // e C/G simultâneos ou U/V repetidos.
        positions.forEach { addCombination(listOf(it)) }
        for (first in positions.indices) {
            for (second in first + 1 until positions.size) {
                addCombination(listOf(positions[first], positions[second]))
            }
        }

        // A variante O/0 da quinta posição é a primeira a ser consultada: é a
        // única posição em que Mercosul e placa antiga se confundem sem que o
        // padrão diga qual caractere deveria existir ali. Depois vêm as trocas
        // simples e, por último, as duplas.
        return ranked.entries
            .sortedWith(
                compareBy<Map.Entry<String, Ranked>> { if (it.value.oZeroFifth) 0 else 1 }
                    .thenBy { it.value.swaps }
                    .thenByDescending { it.value.score }
                    .thenBy { it.key }
            )
            .map { it.key }
            .take(MAX_ALTERNATIVES)
    }

    /**
     * Troca O↔0 somente na quinta posição. É a variante que resolve a leitura
     * de uma placa Mercosul cujo "O" saiu como "0" (virando uma placa antiga
     * válida), e o contrário. Retorna nulo quando a troca não forma placa.
     */
    fun fifthOZeroVariant(normalized: String): String? {
        val value = clean(normalized)
        if (value.length != 7) return null
        val replacement = when (value[4]) {
            'O' -> '0'
            '0' -> 'O'
            else -> return null
        }
        val candidate = buildString {
            append(value, 0, 4)
            append(replacement)
            append(value, 5, value.length)
        }
        return candidate.takeIf { PlatePattern.isPlate(it) }
    }

    private fun isFifthOZeroSwap(primary: String, candidate: String): Boolean {
        if (primary.length != 7 || candidate.length != 7) return false
        if (primary[4] !in "O0" || candidate[4] !in "O0") return false
        if (primary[4] == candidate[4]) return false
        return primary.substring(0, 4) == candidate.substring(0, 4) &&
            primary.substring(5) == candidate.substring(5)
    }

    /**
     * Duas leituras são equivalentes quando diferem apenas em caracteres que o
     * OCR costuma confundir. Usado para aceitar a segunda leitura de um recorte
     * ampliado sem trocar a placa por um veículo completamente diferente.
     */
    fun isVisuallyEquivalent(first: String, second: String): Boolean {
        val a = clean(first)
        val b = clean(second)
        if (a.length != b.length || a.isEmpty()) return false
        return a.indices.all { index ->
            a[index] == b[index] ||
                visualConfusions[a[index]].orEmpty().contains(b[index]) ||
                visualConfusions[b[index]].orEmpty().contains(a[index])
        }
    }

    fun hasConfusableCharacters(plate: String): Boolean =
        plate.any { visualConfusions[it].orEmpty().isNotEmpty() }

    fun display(normalized: String): String = normalized

    private fun tryCorrect(value: String, letterPositions: Set<Int>, digitPositions: Set<Int>): Pair<String, Int>? {
        val chars = value.toCharArray()
        var corrections = 0

        for (index in chars.indices) {
            val char = chars[index]
            when {
                index in letterPositions -> {
                    if (char in 'A'..'Z') continue
                    val mapped = digitToLetter[char] ?: return null
                    chars[index] = mapped
                    corrections++
                }
                index in digitPositions -> {
                    if (char in '0'..'9') continue
                    val mapped = letterToDigit[char] ?: return null
                    chars[index] = mapped
                    corrections++
                }
                else -> return null
            }
            if (corrections > MAX_CORRECTIONS) return null
        }

        val result = String(chars)
        return if (PlatePattern.isPlate(result)) result to corrections else null
    }
}
