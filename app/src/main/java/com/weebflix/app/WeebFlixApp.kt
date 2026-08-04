package com.weebflix.app

import android.app.Application
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.provider.AnimeProvider
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.SamehadakuScraper

class WeebFlixApp : Application() {

    lateinit var scraper: SamehadakuScraper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        ProviderConfig.init(this)
        YouTubeAuthManager.init(this)
        scraper = SamehadakuScraper()
        ProviderFactory.getAllProviders()
    }

    fun getActiveProvider(): AnimeProvider {
        return ProviderFactory.getActiveProvider()
    }

    companion object {
        lateinit var instance: WeebFlixApp
            private set
    }
}
