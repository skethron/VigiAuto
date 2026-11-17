package br.com.vigiauto

import br.com.vigiauto.domain.model.NmtCategoryPolicy
import br.com.vigiauto.domain.model.NmtStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NmtCategoryPolicyTest {

    @Test
    fun `falha temporaria preserva categoria concluida`() {
        listOf("REGULAR", "IRREGULAR", "NOT_REGISTERED").forEach { previous ->
            assertTrue(
                NmtCategoryPolicy.shouldPreserve(previous, NmtStatus.UNAVAILABLE)
            )
            assertTrue(
                NmtCategoryPolicy.shouldPreserve(previous, NmtStatus.UNKNOWN)
            )
        }
    }

    @Test
    fun `resultado concluido substitui categoria anterior`() {
        assertFalse(
            NmtCategoryPolicy.shouldPreserve("NOT_REGISTERED", NmtStatus.REGULAR)
        )
        assertFalse(
            NmtCategoryPolicy.shouldPreserve("REGULAR", NmtStatus.NOT_REGISTERED)
        )
    }

    @Test
    fun `sem categoria anterior a falha pode ser gravada`() {
        assertFalse(NmtCategoryPolicy.shouldPreserve(null, NmtStatus.UNAVAILABLE))
        assertFalse(NmtCategoryPolicy.shouldPreserve("UNAVAILABLE", NmtStatus.UNKNOWN))
        assertFalse(NmtCategoryPolicy.shouldPreserve("lixo", NmtStatus.UNAVAILABLE))
    }
}
