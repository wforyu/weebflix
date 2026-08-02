package com.weebflix.app.data.scraper

/**
 * Builds an on-demand DASH manifest (single segment per representation, byte-range based)
 * from YouTube adaptive formats so ExoPlayer can do adaptive bitrate switching (ABR),
 * YouTube-style: starts low, climbs with bandwidth, drops when throttled. Each format is
 * a separate Representation with its own BaseURL; video formats are grouped per codec so
 * only same-codec reps switch seamlessly.
 */
object YouTubeDashManifest {

    /**
     * Returns a DASH XML manifest for the given formats, or null when too few formats carry
     * byte ranges (initRange + indexRange) to describe single-segment representations.
     * @param maxHeight cap resolution (data saving), default 1080p
     */
    fun build(resolved: ResolvedYouTube, maxHeight: Int = 1080): String? {
        val videoMp4 = resolved.videoFormats
            .filter { it.isVideo && it.height in 1..maxHeight }
            .filter { hasRanges(it) }
            .filter { baseMime(it.mimeType) == "video/mp4" }
            .distinctBy { it.itag }
        val videoWebm = resolved.videoFormats
            .filter { it.isVideo && it.height in 1..maxHeight }
            .filter { hasRanges(it) }
            .filter { baseMime(it.mimeType) == "video/webm" }
            .distinctBy { it.itag }
        val audioOpus = resolved.audioFormats
            .filter { !it.isVideo && hasRanges(it) }
            .filter { it.codecs.contains("opus") || baseMime(it.mimeType) == "audio/webm" }
            .distinctBy { it.itag }
        val audioMp4 = resolved.audioFormats
            .filter { !it.isVideo && hasRanges(it) }
            .filter { baseMime(it.mimeType) == "audio/mp4" }
            .distinctBy { it.itag }

        val videoSets = mutableListOf<String>()
        if (videoMp4.isNotEmpty()) videoSets += videoAdaptationSet(0, videoMp4)
        if (videoWebm.isNotEmpty()) videoSets += videoAdaptationSet(2, videoWebm)
        val audioSets = mutableListOf<String>()
        if (audioOpus.isNotEmpty()) audioSets += audioAdaptationSet(1, audioOpus)
        if (audioMp4.isNotEmpty()) audioSets += audioAdaptationSet(3, audioMp4)
        if (videoSets.isEmpty() || audioSets.isEmpty()) return null

        val durationMs = durationMs(resolved)
        val durationIso = isoDuration(durationMs)

        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" profiles="urn:mpeg:dash:profile:isoff-on-demand:2011" type="static" mediaPresentationDuration="$durationIso" minBufferTime="PT1.5S">""").append('\n')
        sb.append("""<Period id="0" start="PT0S" duration="$durationIso">""").append('\n')
        videoSets.forEach { sb.append(it) }
        audioSets.forEach { sb.append(it) }
        sb.append("""</Period>""").append('\n')
        sb.append("""</MPD>""")
        return sb.toString()
    }

    /** Real duration from the player response, else estimate from the biggest format. */
    private fun durationMs(resolved: ResolvedYouTube): Long {
        if (resolved.durationMs > 0) return resolved.durationMs
        val f = resolved.videoFormats.maxByOrNull { it.bitrate } ?: return 0
        return if (f.contentLength > 0 && f.bitrate > 0) f.contentLength * 8 * 1000 / f.bitrate else 0
    }

    private fun isoDuration(ms: Long): String {
        val totalSec = (ms + 999) / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return buildString {
            append("PT")
            if (h > 0) append(h).append('H')
            if (m > 0) append(m).append('M')
            append(s).append('S')
        }
    }

    private fun hasRanges(s: YouTubeStream): Boolean =
        s.initRange.isNotEmpty() && s.indexRange.isNotEmpty()

    private fun videoAdaptationSet(id: Int, formats: List<YouTubeStream>): String {
        val sb = StringBuilder()
        sb.append("""<AdaptationSet id="$id" contentType="video" segmentAlignment="true" startWithSAP="1">""").append('\n')
        for (f in formats) {
            val codecs = f.codecs.takeIf { it.isNotEmpty() }?.let { """ codecs="$it"""" } ?: ""
            val frameRate = if (f.frameRate > 0) """ frameRate="${f.frameRate}"""" else ""
            val bw = f.bitrate.takeIf { it > 0 } ?: 1L
            sb.append("""<Representation id="${f.itag}" mimeType="${baseMime(f.mimeType)}"$codecs bandwidth="$bw" width="${f.width}" height="${f.height}"$frameRate>""").append('\n')
            sb.append("""<BaseURL>${escapeXml(f.url)}</BaseURL>""").append('\n')
            sb.append(segmentBase(f))
            sb.append("""</Representation>""").append('\n')
        }
        sb.append("""</AdaptationSet>""").append('\n')
        return sb.toString()
    }

    private fun audioAdaptationSet(id: Int, formats: List<YouTubeStream>): String {
        val sb = StringBuilder()
        sb.append("""<AdaptationSet id="$id" contentType="audio" segmentAlignment="true">""").append('\n')
        for (f in formats) {
            val codecs = f.codecs.takeIf { it.isNotEmpty() }?.let { """ codecs="$it"""" } ?: ""
            val bw = f.bitrate.takeIf { it > 0 } ?: 1L
            sb.append("""<Representation id="${f.itag}" mimeType="${baseMime(f.mimeType)}"$codecs bandwidth="$bw">""").append('\n')
            sb.append("""<BaseURL>${escapeXml(f.url)}</BaseURL>""").append('\n')
            sb.append(segmentBase(f))
            sb.append("""</Representation>""").append('\n')
        }
        sb.append("""</AdaptationSet>""").append('\n')
        return sb.toString()
    }

    private fun segmentBase(f: YouTubeStream): String =
        """<SegmentBase indexRange="${f.indexRange}">""" + '\n' +
            """<Initialization range="${f.initRange}"/>""" + '\n' +
            """</SegmentBase>""" + '\n'

    private fun baseMime(full: String): String {
        val base = full.substringBefore(";").trim()
        return if (base.isNotEmpty()) base else if (full.startsWith("video/")) "video/mp4" else "audio/mp4"
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
