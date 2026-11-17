package br.com.vigiauto.domain.plate

object PlatePattern {
    private val OLD = Regex("^[A-Z]{3}[0-9]{4}$")
    private val MERCOSUL = Regex("^[A-Z]{3}[0-9][A-Z][0-9]{2}$")

    fun isPlate(value: String): Boolean = OLD.matches(value) || MERCOSUL.matches(value)

    fun isOld(value: String): Boolean = OLD.matches(value)

    fun isMercosul(value: String): Boolean = MERCOSUL.matches(value)
}
