package br.com.vigiauto

import br.com.vigiauto.data.lookup.LookupRouter
import br.com.vigiauto.domain.model.LookupMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupRouterTest {

    @Test
    fun `sinesp sem token cai automaticamente na consulta simples`() {
        assertTrue(LookupRouter.shouldUseSimpleFallback(LookupMode.SINESP, false))
        assertFalse(LookupRouter.shouldUseSimpleFallback(LookupMode.SINESP, true))
        assertFalse(LookupRouter.shouldUseSimpleFallback(LookupMode.REMOTE, false))
        assertFalse(LookupRouter.shouldUseSimpleFallback(LookupMode.HTTP, false))
        assertFalse(LookupRouter.shouldUseSimpleFallback(LookupMode.DEMO, false))
    }
}
