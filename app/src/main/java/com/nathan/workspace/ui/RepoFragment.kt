package com.nathan.workspace.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.button.MaterialButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.card.MaterialCardView
import com.nathan.workspace.R
import com.nathan.workspace.api.AssetInfo
import com.nathan.workspace.api.GitHubApi
import com.nathan.workspace.api.ReleaseInfo
import com.nathan.workspace.databinding.FragmentRepoBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RepoFragment : Fragment() {

    private var _binding: FragmentRepoBinding? = null
    private val binding get() = _binding!!

    private val api = GitHubApi
    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val downloadJobs = mutableMapOf<Long, Job>()
    private val downloadProgress = mutableMapOf<Long, Int>()

    private var releases = emptyList<ReleaseInfo>()
    private var token: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRepoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        token = requireActivity().getSharedPreferences("app", Context.MODE_PRIVATE)
            .getString("github_token", "") ?: ""
        setupRecyclerView()
        setupListeners()
        loadReleases()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadScope.cancel()
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

    private fun downloadAndInstall(asset: AssetInfo, btnAction: MaterialButton, progressBar: ProgressBar) {
        if (downloadJobs.containsKey(asset.id)) return
        val job = downloadScope.launch {
            try {
                btnAction.isEnabled = false
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
                btnAction.text = "0%"

                val file = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder().url(asset.browserDownloadUrl)
                        .header("Authorization", "Bearer $token")
                        .build()
                    val response = client.newCall(request).execute()
                    if (!response.isSuccessful) throw Exception("Download gagal (${response.code})")
                    val body = response.body ?: throw Exception("Body kosong")
                    val totalBytes = body.contentLength()
                    val dir = File(requireContext().cacheDir, "downloads")
                    dir.mkdirs()
                    val outputFile = File(dir, asset.name)
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        val input = body.byteStream()
                        var bytesRead: Long = 0
                        var bytes = input.read(buffer)
                        while (bytes >= 0) {
                            output.write(buffer, 0, bytes)
                            bytesRead += bytes
                            if (totalBytes > 0) {
                                val pct = ((bytesRead * 100) / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    progressBar.progress = pct
                                    btnAction.text = "$pct%"
                                    downloadProgress[asset.id] = pct
                                }
                            }
                            bytes = input.read(buffer)
                        }
                    }
                    outputFile
                }

                progressBar.visibility = View.GONE
                btnAction.text = "Install"
                btnAction.icon = requireContext().getDrawable(R.drawable.ic_install)
                btnAction.isEnabled = true
                downloadJobs.remove(asset.id)
                downloadProgress.remove(asset.id)

                btnAction.setOnClickListener { installApk(file) }

            } catch (e: CancellationException) {
                btnAction.isEnabled = true
                progressBar.visibility = View.GONE
                btnAction.text = "Download"
                btnAction.icon = requireContext().getDrawable(R.drawable.ic_download)
                downloadJobs.remove(asset.id)
                downloadProgress.remove(asset.id)
            } catch (e: Exception) {
                btnAction.isEnabled = true
                progressBar.visibility = View.GONE
                btnAction.text = "Download"
                btnAction.icon = requireContext().getDrawable(R.drawable.ic_download)
                downloadJobs.remove(asset.id)
                downloadProgress.remove(asset.id)
                if (isAdded) Toast.makeText(requireContext(), "Download gagal: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        downloadJobs[asset.id] = job
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
            // Fallback: try opening via browser
            Toast.makeText(requireContext(), "Buka pengaturan untuk izinkan install dari sumber tidak dikenal", Toast.LENGTH_LONG).show()
        }
    }

    private inner class ReleasesAdapter : RecyclerView.Adapter<ReleasesAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_release, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val release = releases[position]
            holder.bind(release)
        }

        override fun getItemCount() = releases.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
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
                        val btnAction = assetView.findViewById<MaterialButton>(R.id.btn_asset_action)
                        val progressBar = assetView.findViewById<ProgressBar>(R.id.progress_download)

                        tvName.text = asset.name
                        tvInfo.text = "${formatSize(asset.size)} · ${NumberFormat.getNumberInstance(Locale.US).format(asset.downloadCount)} download"

                        val existingProgress = downloadProgress[asset.id]
                        if (existingProgress != null && downloadJobs.containsKey(asset.id)) {
                            btnAction.text = "$existingProgress%"
                            progressBar.visibility = View.VISIBLE
                            progressBar.progress = existingProgress
                            btnAction.isEnabled = false
                        } else {
                            btnAction.text = "Download"
                            btnAction.icon = itemView.context.getDrawable(R.drawable.ic_download)
                            progressBar.visibility = View.GONE
                        }

                        btnAction.setOnClickListener {
                            if (downloadJobs.containsKey(asset.id)) return@setOnClickListener
                            downloadAndInstall(asset, btnAction, progressBar)
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
