package br.com.begood.bmp

import android.app.Application
import br.com.begood.bmp.data.Capas
import br.com.begood.bmp.data.Estatisticas
import br.com.begood.bmp.data.Genius
import br.com.begood.bmp.data.Playlists
import br.com.begood.bmp.data.Preferencias
import br.com.begood.bmp.data.Rede
import br.com.begood.bmp.data.Repositorio
import br.com.begood.bmp.data.Tags
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Preferencias.iniciar(this)
        Repositorio.iniciar(this)
        Tags.iniciar()
        Estatisticas.iniciar(this)
        Playlists.iniciar(this)
        Genius.iniciar(this)
    }

    /** Capas: as embutidas nas músicas (bmpcapa://) e as da internet (busca de álbum). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(Capas.ChaveCoil())
                add(Capas.FabricaCoil(this@App))
                add(OkHttpNetworkFetcherFactory(callFactory = { Rede.http }))
            }
            .crossfade(true)
            .build()
}
