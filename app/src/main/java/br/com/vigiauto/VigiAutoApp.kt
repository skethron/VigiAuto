package br.com.vigiauto

import android.app.Application
import br.com.vigiauto.core.AppContainer

class VigiAutoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.repository.start()
        // O Sinesp v1 é o padrão e não usa o remote config do Consulta Placa.
        // A Consulta Simples busca a configuração sob demanda na primeira consulta.
    }
}
