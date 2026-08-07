package com.weebflix.app.ui.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.weebflix.app.R
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.home.HomeFragment
import com.weebflix.app.ui.ongoing.OngoingFragment
import com.weebflix.app.ui.search.SearchFragment
import com.weebflix.app.ui.settings.SettingsFragment
import com.weebflix.app.ui.util.Insets
import com.weebflix.app.ui.util.TvUtils
import com.weebflix.app.ui.util.padSystemBars
import com.weebflix.app.ui.youtube.YouTubeHistoryFragment
import com.weebflix.app.ui.youtube.YouTubeHomeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private var homeFragment: HomeFragment? = null
    private var searchFragment: SearchFragment? = null
    private var ongoingFragment: OngoingFragment? = null
    private var youtubeHistoryFragment: YouTubeHistoryFragment? = null
    private var youtubeFragment: YouTubeHomeFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TvUtils.forceLandscapeOnTv(this)
        setContentView(R.layout.activity_main)

        Insets.edgeToEdge(this)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.itemIconTintList = null
        findViewById<View>(R.id.fragmentContainer).padSystemBars(top = true, bottom = false)
        bottomNav.padSystemBars(top = false, bottom = true)

        if (savedInstanceState == null) {
            showFragment(getHomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showFragment(getHomeFragment())
                    true
                }
                R.id.nav_search -> {
                    showFragment(getSearchFragment())
                    true
                }
                R.id.nav_ongoing -> {
                    if (ProviderConfig.activeProviderId == ProviderFactory.YOUTUBE_ID) {
                        showFragment(getYouTubeHistoryFragment())
                    } else {
                        showFragment(getOngoingFragment())
                    }
                    true
                }
                R.id.nav_youtube -> {
                    showFragment(getYouTubeFragment())
                    true
                }
                R.id.nav_settings -> {
                    showFragment(getSettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
        updateNavLabels()
    }

    internal fun updateNavLabels() {
        val isYt = ProviderConfig.activeProviderId == ProviderFactory.YOUTUBE_ID
        bottomNav.menu.findItem(R.id.nav_ongoing).title =
            if (isYt) getString(R.string.history) else getString(R.string.ongoing)
    }

    private fun showFragment(target: Fragment) {
        if (target === activeFragment) return
        val transaction = supportFragmentManager.beginTransaction()
        activeFragment?.let { transaction.hide(it) }
        if (target.isAdded) {
            transaction.show(target)
        } else {
            transaction.add(R.id.fragmentContainer, target)
        }
        transaction.commit()
        activeFragment = target
    }

    private fun getHomeFragment(): HomeFragment {
        homeFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("home") as? HomeFragment
            ?: HomeFragment().also {
                homeFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "home").hide(it).commitNow()
            }
    }

    private fun getSearchFragment(): SearchFragment {
        searchFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("search") as? SearchFragment
            ?: SearchFragment().also {
                searchFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "search").hide(it).commitNow()
            }
    }

    private fun getOngoingFragment(): OngoingFragment {
        ongoingFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("ongoing") as? OngoingFragment
            ?: OngoingFragment().also {
                ongoingFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "ongoing").hide(it).commitNow()
            }
    }

    private fun getYouTubeFragment(): YouTubeHomeFragment {
        youtubeFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("youtube") as? YouTubeHomeFragment
            ?: YouTubeHomeFragment().also {
                youtubeFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "youtube").hide(it).commitNow()
            }
    }

    private fun getYouTubeHistoryFragment(): YouTubeHistoryFragment {
        youtubeHistoryFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("yt_history") as? YouTubeHistoryFragment
            ?: YouTubeHistoryFragment().also {
                youtubeHistoryFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "yt_history").hide(it).commitNow()
            }
    }

    private fun getSettingsFragment(): SettingsFragment {
        settingsFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("settings") as? SettingsFragment
            ?: SettingsFragment().also {
                settingsFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "settings").hide(it).commitNow()
            }
    }
}
