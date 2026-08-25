package com.weebflix.app

import android.app.Application
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.provider.AnimeProvider
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.SamehadakuScraper
import com.weebflix.app.data.scraper.YouTubeSubscriptionStore

class WeebFlixApp : Application() {

    lateinit var scraper: SamehadakuScraper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        ProviderConfig.init(this)
        YouTubeAuthManager.init(this)
        YouTubeSubscriptionStore.init(this)
        scraper = SamehadakuScraper()
        ProviderFactory.getAllProviders()
        // Pre-initialize PO Token BotGuard in background (for YouTube playback)
        com.weebflix.app.data.scraper.YouTubeResolver.initPoToken(this)
    }

    fun getActiveProvider(): AnimeProvider {
        return ProviderFactory.getActiveProvider()
    }

    companion object {
        lateinit var instance: WeebFlixApp
            private set
    }
}
