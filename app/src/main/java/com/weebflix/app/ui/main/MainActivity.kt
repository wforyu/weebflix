package com.weebflix.app.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.weebflix.app.R
import com.weebflix.app.ui.home.HomeFragment
import com.weebflix.app.ui.ongoing.OngoingFragment
import com.weebflix.app.ui.search.SearchFragment
import com.weebflix.app.ui.settings.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private var homeFragment: HomeFragment? = null
    private var searchFragment: SearchFragment? = null
    private var ongoingFragment: OngoingFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.itemIconTintList = null

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
                    showFragment(getOngoingFragment())
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

    private fun getSettingsFragment(): SettingsFragment {
        settingsFragment?.let { return it }
        return supportFragmentManager.findFragmentByTag("settings") as? SettingsFragment
            ?: SettingsFragment().also {
                settingsFragment = it
                supportFragmentManager.beginTransaction().add(R.id.fragmentContainer, it, "settings").hide(it).commitNow()
            }
    }
}
