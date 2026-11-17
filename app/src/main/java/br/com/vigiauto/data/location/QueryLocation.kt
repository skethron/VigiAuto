package br.com.vigiauto.data.location

/**
 * Endereço aproximado do aparelho no momento em que uma placa foi consultada.
 *
 * O número é o mais próximo retornado pelo Geocoder; quando a base não possui
 * numeração, ele fica nulo em vez de ser inventado.
 */
data class QueryLocation(
    val street: String?,
    val number: String?,
    val neighborhood: String?,
    val city: String?,
    val state: String?,
    val postalCode: String?,
    val capturedAt: Long
) {
    val hasAddress: Boolean
        get() = !street.isNullOrBlank() || !number.isNullOrBlank() ||
            !neighborhood.isNullOrBlank() || !city.isNullOrBlank() ||
            !state.isNullOrBlank() || !postalCode.isNullOrBlank()
}
