package com.weebflix.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.weebflix.app.R
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.provider.ProviderFactory

class SettingsFragment : Fragment() {

    private lateinit var chipGroupProviders: ChipGroup
    private lateinit var tvCurrentProvider: TextView
    private lateinit var etBaseUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var tvCurrentUrl: TextView

    private var selectedProviderId: String = ProviderConfig.activeProviderId

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chipGroupProviders = view.findViewById(R.id.chipGroupProviders)
        tvCurrentProvider = view.findViewById(R.id.tvCurrentProvider)
        etBaseUrl = view.findViewById(R.id.etBaseUrl)
        btnSave = view.findViewById(R.id.btnSave)
        btnReset = view.findViewById(R.id.btnReset)
        tvCurrentUrl = view.findViewById(R.id.tvCurrentUrl)

        setupProviderChips()
        loadProviderSettings(selectedProviderId)

        btnSave.setOnClickListener {
            val newUrl = etBaseUrl.text.toString().trim()
            if (newUrl.isEmpty()) {
                etBaseUrl.error = getString(R.string.url_empty)
                return@setOnClickListener
            }
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                etBaseUrl.error = getString(R.string.url_invalid)
                return@setOnClickListener
            }
            ProviderConfig.setBaseUrl(selectedProviderId, newUrl)
            ProviderFactory.refreshBaseUrl(selectedProviderId)
            tvCurrentUrl.text = ProviderConfig.getBaseUrl(selectedProviderId)
            Toast.makeText(requireContext(), getString(R.string.domain_updated), Toast.LENGTH_SHORT).show()
        }

        btnReset.setOnClickListener {
            ProviderConfig.resetBaseUrl(selectedProviderId)
            ProviderFactory.refreshBaseUrl(selectedProviderId)
            loadProviderSettings(selectedProviderId)
            Toast.makeText(requireContext(), getString(R.string.domain_reset), Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupProviderChips() {
        val providers = ProviderFactory.getAllProviders()
        chipGroupProviders.removeAllViews()

        providers.forEach { provider ->
            val chip = Chip(requireContext()).apply {
                text = provider.name
                isCheckable = true
                isChecked = provider.id == selectedProviderId
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
                    selectedProviderId = provider.id
                    updateChipSelection()
                    loadProviderSettings(provider.id)
                }
            }
            chipGroupProviders.addView(chip)
        }
    }

    private fun updateChipSelection() {
        val providers = ProviderFactory.getAllProviders()
        for (i in 0 until chipGroupProviders.childCount) {
            val chip = chipGroupProviders.getChildAt(i) as? Chip
            val provider = providers.getOrNull(i)
            if (chip != null && provider != null) {
                chip.isChecked = provider.id == selectedProviderId
                chip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(
                    if (provider.id == selectedProviderId) 0xFFE50914.toInt() else 0xFF333333.toInt()
                )
                chip.setTextColor(
                    if (provider.id == selectedProviderId) 0xFFFFFFFF.toInt() else 0xFFB3B3B3.toInt()
                )
            }
        }
    }

    private fun loadProviderSettings(providerId: String) {
        val provider = ProviderFactory.getProvider(providerId)
        tvCurrentProvider.text = provider.name
        tvCurrentUrl.text = ProviderConfig.getBaseUrl(providerId)
        etBaseUrl.setText(ProviderConfig.getBaseUrl(providerId))
    }
}
