package com.weebflix.app.ui.settings

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.weebflix.app.BuildConfig
import com.weebflix.app.R
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.config.ProviderConfig
import com.weebflix.app.data.provider.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

class SettingsFragment : Fragment() {

    private lateinit var chipGroupProviders: ChipGroup
    private lateinit var tvCurrentProvider: TextView
    private lateinit var etBaseUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnReset: Button
    private lateinit var tvCurrentUrl: TextView
    private lateinit var tvAppName: TextView
    private lateinit var tvAppVersion: TextView
    private lateinit var tvAppCommit: TextView
    private lateinit var tvAppBuildDate: TextView
    private lateinit var tvAppDeveloper: TextView
    private lateinit var btnCheckUpdate: Button
    private lateinit var ytOAuthSection: LinearLayout
    private lateinit var tvYtAuthStatus: TextView
    private lateinit var etYtClientId: EditText
    private lateinit var etYtClientSecret: EditText
    private lateinit var etYtRedirect: EditText
    private lateinit var btnYtAuthSave: Button
    private lateinit var btnYtLogout: Button

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
        tvAppName = view.findViewById(R.id.tvAppName)
        tvAppVersion = view.findViewById(R.id.tvAppVersion)
        tvAppCommit = view.findViewById(R.id.tvAppCommit)
        tvAppBuildDate = view.findViewById(R.id.tvAppBuildDate)
        tvAppDeveloper = view.findViewById(R.id.tvAppDeveloper)
        btnCheckUpdate = view.findViewById(R.id.btnCheckUpdate)
        ytOAuthSection = view.findViewById(R.id.ytOAuthSection)
        tvYtAuthStatus = view.findViewById(R.id.tvYtAuthStatus)
        etYtClientId = view.findViewById(R.id.etYtClientId)
        etYtClientSecret = view.findViewById(R.id.etYtClientSecret)
        etYtRedirect = view.findViewById(R.id.etYtRedirect)
        btnYtAuthSave = view.findViewById(R.id.btnYtAuthSave)
        btnYtLogout = view.findViewById(R.id.btnYtLogout)

        setupProviderChips()
        loadProviderSettings(selectedProviderId)
        setupAppInfo()

        btnYtAuthSave.setOnClickListener {
            ProviderConfig.setYtOAuthClientId(etYtClientId.text.toString())
            ProviderConfig.setYtOAuthClientSecret(etYtClientSecret.text.toString())
            ProviderConfig.setYtOAuthRedirectUri(etYtRedirect.text.toString())
            etYtRedirect.setText(ProviderConfig.getYtOAuthRedirectUri())
            updateYtAuthStatus()
            Toast.makeText(requireContext(), "OAuth tersimpan", Toast.LENGTH_SHORT).show()
        }

        btnYtLogout.setOnClickListener {
            YouTubeAuthManager.logout()
            updateYtAuthStatus()
            Toast.makeText(requireContext(), "Berhasil keluar", Toast.LENGTH_SHORT).show()
        }

        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

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

        val isYoutube = providerId == ProviderFactory.YOUTUBE_ID
        ytOAuthSection.visibility = if (isYoutube) View.VISIBLE else View.GONE
        if (isYoutube) {
            etYtClientId.setText(ProviderConfig.getYtOAuthClientId())
            etYtClientSecret.setText(ProviderConfig.getYtOAuthClientSecret())
            etYtRedirect.setText(ProviderConfig.getYtOAuthRedirectUri())
            updateYtAuthStatus()
        }
    }

    private fun updateYtAuthStatus() {
        val loggedIn = YouTubeAuthManager.isLoggedIn()
        tvYtAuthStatus.text = when {
            loggedIn -> "Masuk sebagai ${YouTubeAuthManager.email()}\nVideo yang diblokir (Content ID / butuh login) bisa diputar."
            else -> "Belum login. Buka tab YouTube → tombol Masuk.\n\nOAuth Client ID sudah tertanam di app (default). Pastikan email kamu terdaftar sebagai Test User di Google Cloud Console (OAuth consent screen → Test users)."
        }
        btnYtLogout.visibility = if (loggedIn) View.VISIBLE else View.GONE
    }

    private fun setupAppInfo() {
        tvAppName.text = getString(R.string.app_name)
        tvAppVersion.text = "${getString(R.string.version)} ${BuildConfig.VERSION_NAME} (${getString(R.string.build_type)}: ${BuildConfig.BUILD_TYPE}, ${getString(R.string.version_code)} ${BuildConfig.VERSION_CODE})"
        tvAppCommit.text = "${getString(R.string.build_commit)} ${BuildConfig.GIT_COMMIT}"
        tvAppBuildDate.text = "${getString(R.string.build_date)} ${BuildConfig.BUILD_DATE}"
        tvAppDeveloper.text = getString(R.string.developer_by)
    }

    /**
     * Checks the latest GitHub release (wforyu/weebflix) against the installed
     * version. If a newer release exists, offers to open its download page.
     */
    private fun checkForUpdate() {
        val client = OkHttpClient()
        lifecycleScope.launch {
            Toast.makeText(requireContext(), getString(R.string.update_checking), Toast.LENGTH_SHORT).show()
            val release = withContext(Dispatchers.IO) {
                try {
                    // `releases/latest` returns 404 when all releases are pre-releases,
                    // so fetch the newest release from the list instead.
                    val request = Request.Builder()
                        .url("https://api.github.com/repos/wforyu/weebflix/releases?per_page=1")
                        .header("Accept", "application/vnd.github+json")
                        .header("User-Agent", "WeebFlix")
                        .build()
                    client.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val arr = JSONArray(resp.body?.string() ?: "")
                        if (arr.length() == 0) return@use null
                        val json = arr.getJSONObject(0)
                        json.optString("tag_name", "") to json.optString("html_url", "")
                    }
                } catch (e: Exception) {
                    null
                }
            } ?: run {
                Toast.makeText(requireContext(), getString(R.string.update_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            val (tag, url) = release
            val latest = tag.removePrefix("v").trim()
            val current = BuildConfig.VERSION_NAME.trim()
            if (latest.isEmpty() || latest == current || !isNewer(latest, current)) {
                Toast.makeText(requireContext(), getString(R.string.update_latest), Toast.LENGTH_SHORT).show()
                return@launch
            }
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.update_available))
                .setMessage("Versi terbaru: v$latest\nVersi terpasang: v$current\n\nUnduh dari halaman rilis GitHub.")
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(getString(R.string.update_download)) { _, _ ->
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                .show()
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            Regex("""\d+""").findAll(v).map { it.value.toInt() }.toList()
        val a = parts(latest)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
