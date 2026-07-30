package com.nathan.workspace.ui

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.nathan.workspace.R
import com.nathan.workspace.api.AssetInfo
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.api.ReleaseInfo
import com.nathan.workspace.databinding.FragmentRepoBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class RepoFragment : Fragment() {

    private var _binding: FragmentRepoBinding? = null
    private val binding get() = _binding!!

    private val api = GitHubApi
    private var releases = emptyList<ReleaseInfo>()
    private var token: String = ""
    private var showAllReleases = false

    private lateinit var downloadManager: DownloadManager
    private lateinit var prefs: SharedPreferences

    // Track active downloads: DownloadManager ID -> Asset ID
    private val activeDownloads = mutableMapOf<Long, Long>()
    
    // UI state for progress
    private val progressMap = mutableMapOf<Long, Int>() // Asset ID -> Progress %
    private val speedMap = mutableMapOf<Long, String>() // Asset ID -> Speed text
    
    // For speed calculation
    private val lastBytesMap = mutableMapOf<Long, Long>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRepoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val appPrefs = requireActivity().getSharedPreferences("app", Context.MODE_PRIVATE)
        token = appPrefs.getString("github_token", "") ?: ""
        
        downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        prefs = requireContext().getSharedPreferences("downloads_repo", Context.MODE_PRIVATE)
        
        restoreActiveDownloads()

        setupRecyclerView()
        setupListeners()
        loadReleases()
        
        startDownloadPolling()
    }

    private fun restoreActiveDownloads() {
        activeDownloads.clear()
        prefs.all.forEach { (key, value) ->
            try {
                val dmId = key.toLong()
                val assetId = (value as? Long) ?: (value as? String)?.toLong() ?: return@forEach
                activeDownloads[dmId] = assetId
            } catch (_: Exception) {}
        }
    }

    private fun saveActiveDownload(dmId: Long, assetId: Long) {
        activeDownloads[dmId] = assetId
        prefs.edit().putLong(dmId.toString(), assetId).apply()
    }

    private fun removeActiveDownload(dmId: Long) {
        activeDownloads.remove(dmId)
        prefs.edit().remove(dmId.toString()).apply()
        val assetId = activeDownloads[dmId]
        if (assetId != null) {
            progressMap.remove(assetId)
            speedMap.remove(assetId)
            lastBytesMap.remove(dmId)
        }
    }

    private fun startDownloadPolling() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (activeDownloads.isNotEmpty()) {
                        pollDownloadManager()
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun pollDownloadManager() {
        val query = DownloadManager.Query()
        val cursor: Cursor = try {
            downloadManager.query(query)
        } catch (e: Exception) { return }

        val currentValidIds = mutableSetOf<Long>()
        var uiNeedsUpdate = false

        if (cursor.moveToFirst()) {
            do {
                val idIndex = cursor.getColumnIndex(DownloadManager.COLUMN_ID)
                val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                
                if (idIndex < 0 || statusIndex < 0 || bytesDownloadedIndex < 0 || bytesTotalIndex < 0) continue

                val dmId = cursor.getLong(idIndex)
                val assetId = activeDownloads[dmId] ?: continue
                currentValidIds.add(dmId)

                val status = cursor.getInt(statusIndex)
                val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                val bytesTotal = cursor.getLong(bytesTotalIndex)

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                        removeActiveDownload(dmId)
                        uiNeedsUpdate = true
                    }
                    DownloadManager.STATUS_RUNNING -> {
                        val progress = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt() else 0
                        progressMap[assetId] = progress
                        
                        val lastBytes = lastBytesMap[dmId] ?: 0L
                        val speedBytes = bytesDownloaded - lastBytes
                        lastBytesMap[dmId] = bytesDownloaded
                        
                        val speedText = "${formatSize(speedBytes)}/s"
                        val sizeText = "${formatSize(bytesDownloaded)} / ${formatSize(bytesTotal)}"
                        speedMap[assetId] = "$sizeText ($speedText)"
                        uiNeedsUpdate = true
                    }
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        val missingIds = activeDownloads.keys - currentValidIds
        missingIds.forEach { 
            removeActiveDownload(it)
            uiNeedsUpdate = true
        }

        if (uiNeedsUpdate) {
            binding.rvReleases.adapter?.notifyDataSetChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupRecyclerView() {
        binding.rvReleases.layoutManager = LinearLayoutManager(requireContext())
        binding.rvReleases.adapter = ReleasesAdapter()
    }

    private fun setupListeners() {
        binding.btnOpenRepo.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NathanKanaeru/samptest"))
            startActivity(intent)
        }
        binding.swipeRefresh.setOnRefreshListener { loadReleases() }
    }

    private fun loadReleases() {
        if (token.isBlank()) {
            showError("Token tidak tersedia. Silakan login ulang.")
            return
        }
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            val result = api.getReleases(token)
            if (!isAdded) return@launch
            binding.swipeRefresh.isRefreshing = false
            result.fold(
                onSuccess = { list ->
                    releases = list
                    if (list.isEmpty()) showEmpty()
                    else showContent()
                    binding.rvReleases.adapter?.notifyDataSetChanged()
                },
                onFailure = { e ->
                    if (releases.isEmpty()) showError(e.message ?: "Gagal memuat rilis")
                    else Toast.makeText(requireContext(), "Gagal refresh: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun showLoading() {
        binding.layoutLoading.visibility = View.VISIBLE
        binding.rvReleases.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.tvError.visibility = View.GONE
    }

    private fun showContent() {
        binding.layoutLoading.visibility = View.GONE
        binding.rvReleases.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        binding.tvError.visibility = View.GONE
    }

    private fun showEmpty() {
        binding.layoutLoading.visibility = View.GONE
        binding.rvReleases.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE
    }

    private fun showError(msg: String) {
        binding.layoutLoading.visibility = View.GONE
        binding.rvReleases.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = msg
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val date = sdf.parse(isoDate) ?: return isoDate
            val outFmt = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
            outFmt.format(date)
        } catch (_: Exception) { isoDate }
    }

    private fun formatSize(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
        else String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    }

    private fun startDownload(asset: AssetInfo) {
        try {
            val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && !dir.exists()) dir.mkdirs()
            
            // Delete existing file to prevent (1), (2) suffixes and save space
            val existingFile = File(dir, asset.name)
            if (existingFile.exists()) {
                existingFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(asset.browserDownloadUrl))
                .setTitle(asset.name)
                .setDescription("Downloading asset from GitHub")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(requireContext(), Environment.DIRECTORY_DOWNLOADS, asset.name)
                .addRequestHeader("Authorization", "Bearer $token")

            val downloadId = downloadManager.enqueue(request)
            saveActiveDownload(downloadId, asset.id)
            
            progressMap[asset.id] = 0
            speedMap[asset.id] = "Starting..."
            binding.rvReleases.adapter?.notifyDataSetChanged()
            
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to start download: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Buka pengaturan untuk izinkan install dari sumber tidak dikenal", Toast.LENGTH_LONG).show()
        }
    }

    private inner class ReleasesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private val TYPE_ITEM = 0
        private val TYPE_FOOTER = 1

        override fun getItemViewType(position: Int): Int {
            if (!showAllReleases && releases.size > 3 && position == 3) return TYPE_FOOTER
            return TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == TYPE_FOOTER) {
                val btn = MaterialButton(parent.context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 8, 0, 16)
                    }
                    text = "View All Releases"
                    setOnClickListener {
                        showAllReleases = true
                        notifyDataSetChanged()
                    }
                }
                return object : RecyclerView.ViewHolder(btn) {}
            }
            
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_release, parent, false)
            return ReleaseViewHolder(view)
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (getItemViewType(position) == TYPE_ITEM) {
                (holder as ReleaseViewHolder).bind(releases[position])
            }
        }

        override fun getItemCount(): Int {
            if (showAllReleases) return releases.size
            return if (releases.size > 3) 4 else releases.size
        }

        inner class ReleaseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvTag = itemView.findViewById<TextView>(R.id.tv_tag)
            private val tvDate = itemView.findViewById<TextView>(R.id.tv_date)
            private val tvReleaseName = itemView.findViewById<TextView>(R.id.tv_release_name)
            private val tvReleaseNotes = itemView.findViewById<TextView>(R.id.tv_release_notes)
            private val tvAssetsHeader = itemView.findViewById<TextView>(R.id.tv_assets_header)
            private val layoutAssets = itemView.findViewById<LinearLayout>(R.id.layout_assets)
            private val btnOpenGithub = itemView.findViewById<MaterialButton>(R.id.btn_open_github)

            fun bind(release: ReleaseInfo) {
                tvTag.text = release.tagName
                tvDate.text = formatDate(release.publishedAt)
                tvReleaseName.text = release.name.ifBlank { release.tagName }
                tvReleaseNotes.text = release.body.ifBlank { "Tidak ada catatan rilis" }

                if (release.assets.isNotEmpty()) {
                    tvAssetsHeader.visibility = View.VISIBLE
                    layoutAssets.visibility = View.VISIBLE
                    layoutAssets.removeAllViews()
                    for (asset in release.assets) {
                        val assetView = LayoutInflater.from(itemView.context)
                            .inflate(R.layout.item_asset, layoutAssets, false)
                            
                        val tvName = assetView.findViewById<TextView>(R.id.tv_asset_name)
                        val tvInfo = assetView.findViewById<TextView>(R.id.tv_asset_info)
                        val tvSpeed = assetView.findViewById<TextView>(R.id.tv_download_speed)
                        val btnAction = assetView.findViewById<MaterialButton>(R.id.btn_asset_action)
                        val progressBar = assetView.findViewById<LinearProgressIndicator>(R.id.progress_download)

                        tvName.text = asset.name
                        tvInfo.text = "${formatSize(asset.size)} · ${NumberFormat.getNumberInstance(Locale.US).format(asset.downloadCount)} downloads"

                        val dir = requireContext().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        val downloadedFile = if (dir != null) File(dir, asset.name) else null
                        val isDownloaded = downloadedFile?.exists() == true
                        val isActive = activeDownloads.values.contains(asset.id)

                        if (isActive) {
                            val progress = progressMap[asset.id] ?: 0
                            val speed = speedMap[asset.id] ?: "Downloading..."
                            
                            progressBar.visibility = View.VISIBLE
                            progressBar.progress = progress
                            
                            tvSpeed.visibility = View.VISIBLE
                            tvSpeed.text = speed
                            
                            btnAction.text = "$progress%"
                            btnAction.icon = null
                            btnAction.isEnabled = false
                        } else if (isDownloaded) {
                            progressBar.visibility = View.GONE
                            tvSpeed.visibility = View.GONE
                            
                            btnAction.text = "Install"
                            btnAction.icon = itemView.context.getDrawable(R.drawable.ic_install)
                            btnAction.isEnabled = true
                            
                            btnAction.setOnClickListener { installApk(downloadedFile!!) }
                        } else {
                            progressBar.visibility = View.GONE
                            tvSpeed.visibility = View.GONE
                            
                            btnAction.text = "Download"
                            btnAction.icon = itemView.context.getDrawable(R.drawable.ic_download)
                            btnAction.isEnabled = true
                            
                            btnAction.setOnClickListener { startDownload(asset) }
                        }

                        layoutAssets.addView(assetView)
                    }
                } else {
                    tvAssetsHeader.visibility = View.GONE
                    layoutAssets.visibility = View.GONE
                }

                btnOpenGithub.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                    itemView.context.startActivity(intent)
                }
            }
        }
    }
}
