package com.weebflix.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.weebflix.app.R
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.ui.main.MainActivity
import com.weebflix.app.ui.youtube.YouTubeHomeFragment

class HomeFragment : Fragment() {

    private lateinit var chipGroupProviders: ChipGroup
    private var currentContentFragment: Fragment? = null
    private var currentProviderId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chipGroupProviders = view.findViewById(R.id.chipGroupProviders)

        setupProviderChips()

        if (savedInstanceState == null) {
            val savedProviderId = ProviderConfig.activeProviderId
            selectProvider(savedProviderId)
        }
    }

    private fun setupProviderChips() {
        val providers = ProviderFactory.getAllProviders()
        chipGroupProviders.removeAllViews()

        providers.forEach { provider ->
            val chip = Chip(requireContext()).apply {
                text = provider.name
                isCheckable = true
                isChecked = provider.id == currentProviderId
                isCheckedIconVisible = false
                isCloseIconVisible = false
                chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (isChecked) 0xFFE50914.toInt() else 0xFF333333.toInt()
                )
                setTextColor(
                    if (isChecked) 0xFFFFFFFF.toInt() else 0xFFB3B3B3.toInt()
                )
                chipStrokeWidth = 0f
                setPadding(32, 0, 32, 0)
                minHeight = 0
                setOnClickListener {
                    selectProvider(provider.id)
                }
            }
            chipGroupProviders.addView(chip)
        }
    }

    private fun selectProvider(providerId: String) {
        currentProviderId = providerId
        ProviderConfig.activeProviderId = providerId
        (activity as? MainActivity)?.updateNavLabels()

        for (i in 0 until chipGroupProviders.childCount) {
            val chip = chipGroupProviders.getChildAt(i) as? Chip
            val provider = ProviderFactory.getAllProviders().getOrNull(i)
            if (chip != null && provider != null) {
                chip.isChecked = provider.id == providerId
                chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (provider.id == providerId) 0xFFE50914.toInt() else 0xFF333333.toInt()
                )
                chip.setTextColor(
                    if (provider.id == providerId) 0xFFFFFFFF.toInt() else 0xFFB3B3B3.toInt()
                )
            }
        }

        val newFragment = when (providerId) {
            ProviderFactory.DRAKORKITA_ID -> DrakorKitaHomeFragment()
            ProviderFactory.OPPADRAMA_ID -> OppaDramaHomeFragment()
            ProviderFactory.ANICHIN_ID -> AnichinHomeFragment()
            ProviderFactory.YOUTUBE_ID -> YouTubeHomeFragment()
            else -> SamehadakuHomeFragment()
        }

        val transaction = parentFragmentManager.beginTransaction()

        currentContentFragment?.let { transaction.remove(it) }

        transaction.add(R.id.providerFragmentContainer, newFragment, "home_$providerId")
        transaction.commit()

        currentContentFragment = newFragment

        scrollToSelectedChip()
    }

    private fun scrollToSelectedChip() {
        for (i in 0 until chipGroupProviders.childCount) {
            val chip = chipGroupProviders.getChildAt(i) as? Chip
            val provider = ProviderFactory.getAllProviders().getOrNull(i)
            if (chip != null && provider != null && provider.id == currentProviderId) {
                chip.post {
                    val chipParent = chip.parent as? View
                    chipParent?.scrollTo(chip.left - chipParent.width / 2 + chip.width / 2, 0)
                }
                break
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentContentFragment = null
    }
}
