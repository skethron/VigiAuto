package br.com.vigiauto.core

import android.content.Context
import br.com.vigiauto.data.db.AppDatabase
import br.com.vigiauto.data.lookup.DemoConsultaPlacaService
import br.com.vigiauto.data.lookup.HttpConsultaPlacaService
import br.com.vigiauto.data.lookup.LookupRouter
import br.com.vigiauto.data.lookup.NmtBackgroundWorker
import br.com.vigiauto.data.lookup.RemoteConfigService
import br.com.vigiauto.data.lookup.RemoteConsultaPlacaService
import br.com.vigiauto.data.lookup.SinespAuthClient
import br.com.vigiauto.data.lookup.SinespConsultaPlacaService
import br.com.vigiauto.data.lookup.SinespV1Source
import br.com.vigiauto.data.location.LocationCapture
import br.com.vigiauto.data.network.ConnectivityMonitor
import br.com.vigiauto.data.photos.FrameRingBuffer
import br.com.vigiauto.data.photos.PhotoStorage
import br.com.vigiauto.data.prefs.SettingsRepository
import br.com.vigiauto.data.repo.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database = AppDatabase.build(context)
    val dao = database.vehicleRecordDao()

    val tokenCipher = TokenCipher()
    val settings = SettingsRepository(context, tokenCipher)
    val photoStorage = PhotoStorage(context)
    val frameBuffer = FrameRingBuffer()
    val connectivity = ConnectivityMonitor(context)
    val locationCapture = LocationCapture(context)

    val remoteConfig = RemoteConfigService()
    val demoService = DemoConsultaPlacaService()
    val remoteService = RemoteConsultaPlacaService(remoteConfig)
    val sinespAuth = SinespAuthClient()
    val sinespService = SinespConsultaPlacaService(
        SinespV1Source(credentials = settings::sinespCredentials)
    )
    val httpService = HttpConsultaPlacaService(settings)
    val lookupRouter = LookupRouter(
        settings,
        demoService,
        remoteService,
        sinespService,
        httpService
    )

    init {
        // Remove o token cifrado da antiga integração Dados API. O modo foi
        // substituído pelo Sinesp v1 e pelo cliente HTTP manual.
        appScope.launch { settings.clearLegacyDadosApiToken() }
        // A 1.5.0 passa a usar o Sinesp v1 como modo padrão também em
        // instalações antigas que ainda estavam na Consulta Simples.
        appScope.launch { settings.migrateToSinespDefaultIfNeeded() }
    }

    val nmtWorker = NmtBackgroundWorker(
        context = context,
        dao = dao,
        scope = appScope
    )

    val repository = VehicleRepository(
        dao = dao,
        lookupRouter = lookupRouter,
        photoStorage = photoStorage,
        frameSource = frameBuffer,
        settings = settings,
        connectivity = connectivity,
        nmtWorker = nmtWorker,
        locationCapture = locationCapture,
        scope = appScope
    )
}
