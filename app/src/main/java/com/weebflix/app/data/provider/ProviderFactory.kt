package com.weebflix.app.data.provider

import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.scraper.AnichinScraper
import com.weebflix.app.data.scraper.DrakorKitaScraper
import com.weebflix.app.data.scraper.MissavScraper
import com.weebflix.app.data.scraper.OppaDramaScraper
import com.weebflix.app.data.scraper.OtakudesuScraper
import com.weebflix.app.data.scraper.SamehadakuScraper
import com.weebflix.app.data.scraper.YouTubeScraper

object ProviderFactory {

    const val SAMEHADAKU_ID = "samehadaku"
    const val DRAKORKITA_ID = "drakorkita"
    const val OPPADRAMA_ID = "oppadrama"
    const val ANICHIN_ID = "anichin"
    const val YOUTUBE_ID = "youtube"
    const val OTAKUDESU_ID = "otakudesu"
    const val MISSAV_ID = "missav"

    private val providers = mutableMapOf<String, AnimeProvider>()

    fun getAllProviders(): List<AnimeProvider> {
        if (providers.isEmpty()) {
            providers[SAMEHADAKU_ID] = SamehadakuScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(SAMEHADAKU_ID)
            }
            providers[DRAKORKITA_ID] = DrakorKitaScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(DRAKORKITA_ID)
            }
            providers[OPPADRAMA_ID] = OppaDramaScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(OPPADRAMA_ID)
            }
            providers[ANICHIN_ID] = AnichinScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(ANICHIN_ID)
            }
            providers[YOUTUBE_ID] = YouTubeScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(YOUTUBE_ID)
            }
            providers[OTAKUDESU_ID] = OtakudesuScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(OTAKUDESU_ID)
            }
            providers[MISSAV_ID] = MissavScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(MISSAV_ID)
            }
        }
        return providers.values.toList()
    }

    fun getEnabledProviders(): List<AnimeProvider> {
        return getAllProviders().filter { ProviderConfig.isProviderEnabled(it.id) }
    }

    fun getProvider(id: String): AnimeProvider {
        getAllProviders()
        return providers[id] ?: providers[SAMEHADAKU_ID]!!
    }

    fun getActiveProvider(): AnimeProvider {
        return getProvider(ProviderConfig.activeProviderId)
    }

    fun refreshBaseUrl(providerId: String) {
        providers[providerId]?.let {
            it.baseUrl = ProviderConfig.getBaseUrl(providerId)
        }
    }

    fun refreshAllBaseUrls() {
        providers.values.forEach {
            it.baseUrl = ProviderConfig.getBaseUrl(it.id)
        }
    }
}
