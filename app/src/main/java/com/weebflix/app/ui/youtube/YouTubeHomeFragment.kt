package com.weebflix.app.ui.youtube

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.weebflix.app.R
import com.weebflix.app.data.auth.YouTubeAuthManager
import com.weebflix.app.data.provider.ProviderFactory
import com.weebflix.app.data.scraper.YouTubeDataApi
import com.weebflix.app.data.scraper.YouTubeScraper
import com.weebflix.app.data.scraper.YouTubeVideo
import com.weebflix.app.ui.player.PlayerActivity
import com.weebflix.app.ui.youtube.adapter.YouTubeFeedAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class YouTubeHomeFragment : Fragment() {

    private lateinit var ytFeed: RecyclerView
    private lateinit var ytError: TextView
    private lateinit var ytRefresh: SwipeRefreshLayout
    private lateinit var ytBtnLogin: View
    private lateinit var ytAccountAvatar: ImageView
    private lateinit var ytAccountName: TextView
    private lateinit var adapter: YouTubeFeedAdapter

    private val scraper by lazy { YouTubeScraper() }
    private var isLoading = false
    private var endReached = false
    private var loadJob: Job? = null
    private var loadedOnce = false

    private val loginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            lifecycleScope.launch(Dispatchers.IO) { YouTubeAuthManager.fetchUserInfo() }
            updateLoginUi()
            Toast.makeText(requireContext(), "Login berhasil: ${YouTubeAuthManager.email()}", Toast.LENGTH_LONG).show()
            refreshFeed()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_youtube, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ytFeed = view.findViewById(R.id.ytFeed)
        ytError = view.findViewById(R.id.ytError)
        ytRefresh = view.findViewById(R.id.ytRefresh)
        ytBtnLogin = view.findViewById(R.id.ytBtnLogin)
        ytAccountAvatar = view.findViewById(R.id.ytAccountAvatar)
        ytAccountName = view.findViewById(R.id.ytAccountName)

        adapter = YouTubeFeedAdapter(
            { video -> openVideo(video) },
            { video -> openChannel(video) }
        )
        ytFeed.layoutManager = LinearLayoutManager(requireContext())
        ytFeed.adapter = adapter

        ytRefresh.setColorSchemeResources(R.color.netflix_red, R.color.white)
        ytRefresh.setOnRefreshListener { refreshFeed() }
        ytError.setOnClickListener { retry() }

        view.findViewById<ImageView>(R.id.ytBtnSearch).setOnClickListener {
            startActivity(Intent(requireContext(), YouTubeSearchActivity::class.java))
        }
        view.findViewById<ImageView>(R.id.ytBtnCast).setOnClickListener {
            Toast.makeText(requireContext(), "Cast belum tersedia di prototype", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<ImageView>(R.id.ytBtnNotifications).setOnClickListener {
            Toast.makeText(requireContext(), "Notifikasi butuh login", Toast.LENGTH_SHORT).show()
        }
        ytBtnLogin.setOnClickListener { onLoginClicked() }

        updateLoginUi()

        ytFeed.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (lm.findLastVisibleItemPosition() >= lm.itemCount - 4) {
                    loadMore()
                }
            }
        })

        if (savedInstanceState == null) {
            loadMore()
        }
    }

    private fun loadMore() {
        if (isLoading || endReached || loadJob?.isActive == true) return
        isLoading = true
        val wasEmpty = adapter.isEmpty
        val job = lifecycleScope.launch {
            val page = try {
                if (wasEmpty) {
                    // First load: top "Langganan" section (logged in) + the first endless batch.
                    if (YouTubeAuthManager.isLoggedIn()) {
                        val section = withContext(Dispatchers.IO) {
                            com.weebflix.app.data.scraper.YouTubeDataApi.getSubscriptionsFeed()
                        }
                        if (section.isNotEmpty()) {
                            adapter.setSection(getString(R.string.yt_subscriptions), section)
                            scraper.markSeen(section.map { it.videoId })
                        }
                    }
                    loadedOnce = true
                    withContext(Dispatchers.IO) { scraper.nextFeedPage() }
                } else {
                    withContext(Dispatchers.IO) { scraper.nextFeedPage() }
                }
            } catch (e: Exception) {
                emptyList()
            }
            when {
                page.isNotEmpty() -> {
                    adapter.append(page, endOfFeed = false)
                    // When the list was empty (first load / after refresh), the RecyclerView
                    // pins the only visible view (the footer) as its scroll anchor, so inserting
                    // items above it leaves the viewport stuck at the END of the list. Force the
                    // feed back to the top so the newest items are visible immediately.
                    if (wasEmpty) ytFeed.scrollToPosition(0)
                }
                adapter.isEmpty -> {
                    endReached = true
                    adapter.setLoading()
                    ytError.visibility = View.VISIBLE
                    ytError.text = "Gagal memuat feed. Ketuk untuk coba lagi."
                }
                else -> {
                    endReached = true
                    adapter.setLoading()
                    adapter.append(emptyList(), endOfFeed = true)
                }
            }
            isLoading = false
        }
        loadJob = job
        // setLoading is posted so notifyItemChanged never fires from inside onScrolled
        // (RecyclerView "Cannot call this method in a scroll callback"). Only apply it if the
        // fetch hasn't already finished, otherwise a stale post would re-show the spinner.
        ytFeed.post {
            if (job.isActive) adapter.setLoading()
        }
    }

    /** Refreshes only the top "Langganan" section after returning from the player (subscriptions
     *  may have changed) without re-scraping the whole endless feed. */
    private fun refreshSection() {
        if (!loadedOnce || !YouTubeAuthManager.isLoggedIn()) return
        lifecycleScope.launch {
            val section = try {
                withContext(Dispatchers.IO) {
                    val subs = com.weebflix.app.data.scraper.YouTubeDataApi.getMySubscriptions()
                    android.util.Log.i("YTSubs", "refreshSection: ${subs.size} channels")
                    com.weebflix.app.data.scraper.YouTubeDataApi.getSubscriptionsFeed()
                }
            } catch (e: Exception) {
                emptyList()
            }
            if (section.isNotEmpty()) adapter.setSection(getString(R.string.yt_subscriptions), section)
        }
    }
    private fun refreshFeed() {
        loadJob?.cancel()
        isLoading = false
        endReached = false
        ytError.visibility = View.GONE
        scraper.resetFeed()
        adapter.clear()
        ytRefresh.isRefreshing = false
        loadMore()
    }

    private fun retry() {
        endReached = false
        ytError.visibility = View.GONE
        loadMore()
    }

    private fun onLoginClicked() {
        if (YouTubeAuthManager.isLoggedIn()) {
            showAccountSheet()
        } else {
            if (!YouTubeAuthManager.isConfigured()) {
                Toast.makeText(
                    requireContext(),
                    "OAuth belum dikonfigurasi. Isi Client ID di Settings → provider YouTube.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            loginLauncher.launch(Intent(requireContext(), YouTubeLoginActivity::class.java))
        }
    }

    /** YouTube-style account bottom sheet: account header, manage Google account,
     *  your channel, add account, sign out. */
    private fun showAccountSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_yt_account, null)
        dialog.setContentView(view)

        val avatar = view.findViewById<ImageView>(R.id.accAvatar)
        val name = view.findViewById<TextView>(R.id.accName)
        val email = view.findViewById<TextView>(R.id.accEmail)
        val channelAvatar = view.findViewById<ImageView>(R.id.accChannelAvatar)

        name.text = YouTubeAuthManager.displayName().ifEmpty { "Akun Google" }
        email.text = YouTubeAuthManager.email()
        val pic = YouTubeAuthManager.picture()
        if (pic.isNotEmpty()) {
            com.bumptech.glide.Glide.with(this)
                .load(pic)
                .placeholder(R.drawable.bg_card)
                .into(avatar)
        }

        view.findViewById<View>(R.id.accManageGoogle).setOnClickListener {
            dialog.dismiss()
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://myaccount.google.com/")))
            }
        }

        // "Saluran Anda": fetch the user's own channel (Data API) and open it.
        view.findViewById<View>(R.id.accMyChannel).setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                val channel = try {
                    withContext(Dispatchers.IO) { YouTubeDataApi.getMyChannel() }
                } catch (e: Exception) {
                    null
                }
                if (!isAdded) return@launch
                if (channel != null && channel.channelId.isNotEmpty()) {
                    val intent = Intent(requireContext(), YouTubeChannelActivity::class.java).apply {
                        putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_ID, channel.channelId)
                        putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_NAME, channel.channelName)
                        putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_THUMB, channel.channelThumb)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(requireContext(), "Saluran tidak ditemukan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        view.findViewById<View>(R.id.accAddAccount).setOnClickListener {
            dialog.dismiss()
            if (YouTubeAuthManager.isConfigured()) {
                loginLauncher.launch(Intent(requireContext(), YouTubeLoginActivity::class.java))
            } else {
                Toast.makeText(requireContext(), "OAuth belum dikonfigurasi", Toast.LENGTH_SHORT).show()
            }
        }

        view.findViewById<View>(R.id.accSignOut).setOnClickListener {
            dialog.dismiss()
            AlertDialog.Builder(requireContext())
                .setTitle("Keluar dari akun?")
                .setMessage("Anda akan keluar dari ${YouTubeAuthManager.email()}")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Keluar") { _, _ ->
                    YouTubeAuthManager.logout()
                    updateLoginUi()
                    adapter.setSection(null, emptyList())
                    Toast.makeText(requireContext(), "Berhasil keluar", Toast.LENGTH_SHORT).show()
                }
                .show()
        }

        dialog.show()
    }

    private fun updateLoginUi() {
        if (!isAdded) return
        if (YouTubeAuthManager.isLoggedIn()) {
            ytAccountAvatar.visibility = View.VISIBLE
            val pic = YouTubeAuthManager.picture()
            if (pic.isNotEmpty()) {
                com.bumptech.glide.Glide.with(this)
                    .load(pic)
                    .placeholder(R.drawable.bg_card)
                    .into(ytAccountAvatar)
            } else {
                ytAccountAvatar.setImageDrawable(null)
            }
            // YouTube-like header: show only the avatar when logged in (name lives in the
            // account bottom sheet).
            ytAccountName.visibility = View.GONE
        } else {
            ytAccountAvatar.visibility = View.GONE
            ytAccountName.visibility = View.VISIBLE
            ytAccountName.setText(R.string.yt_login)
            ytAccountName.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.netflix_red))
        }
    }

    override fun onResume() {
        super.onResume()
        updateLoginUi()
        // Subscriptions may have changed while watching (subscribe button in player) — refresh
        // only the top section, not the whole feed.
        refreshSection()
    }

    private fun openVideo(video: YouTubeVideo) {
        val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
            putExtra("url", video.url)
            putExtra("title", video.title)
            putExtra("episodeNumber", "1")
            putExtra("animeTitle", video.title)
            putExtra("imageUrl", video.thumbnail)
            putExtra("animeUrl", video.url)
            putExtra("providerId", ProviderFactory.YOUTUBE_ID)
            putExtra("nextEpisodeUrl", "")
        }
        startActivity(intent)
    }

    private fun openChannel(video: YouTubeVideo) {
        val intent = Intent(requireContext(), YouTubeChannelActivity::class.java).apply {
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_ID, video.channelId)
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_NAME, video.channel)
            putExtra(YouTubeChannelActivity.EXTRA_CHANNEL_THUMB, video.channelThumb)
        }
        startActivity(intent)
    }
}
