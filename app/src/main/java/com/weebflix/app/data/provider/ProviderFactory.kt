package com.weebflix.app.data.provider

import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.scraper.DrakorKitaScraper
import com.weebflix.app.data.scraper.SamehadakuScraper

object ProviderFactory {

    const val SAMEHADAKU_ID = "samehadaku"
    const val DRAKORKITA_ID = "drakorkita"

    private val providers = mutableMapOf<String, AnimeProvider>()

    fun getAllProviders(): List<AnimeProvider> {
        if (providers.isEmpty()) {
            providers[SAMEHADAKU_ID] = SamehadakuScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(SAMEHADAKU_ID)
            }
            providers[DRAKORKITA_ID] = DrakorKitaScraper().also {
                it.baseUrl = ProviderConfig.getBaseUrl(DRAKORKITA_ID)
            }
        }
        return providers.values.toList()
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
