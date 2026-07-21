package com.weebflix.app

import android.app.Application
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.scraper.SamehadakuScraper

class WeebFlixApp : Application() {

    lateinit var scraper: SamehadakuScraper
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        ProviderConfig.init(this)
        scraper = SamehadakuScraper()
    }

    companion object {
        lateinit var instance: WeebFlixApp
            private set
    }
}
