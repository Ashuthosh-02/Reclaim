package com.reclaim.logic

/**
 * UI rules for Reclaim. Detection uses visible controls and view ids.
 * There is no URL matching in this app.
 */
const val INSTAGRAM_PACKAGE = "com.instagram.android"
const val YOUTUBE_PACKAGE = "com.google.android.youtube"

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val centerX: Int get() = left + width / 2
    val centerY: Int get() = top + height / 2
}

data class VisibleControl(
    val label: String,
    val bounds: Bounds,
    val clickable: Boolean,
    val viewId: String = "",
)

enum class OpenedClip {
    INSTAGRAM_REEL,
    YOUTUBE_SHORT,
}

data class MarkerSet(val groups: List<List<String>>)

object Markers {
    /** About half of the known markers must be on screen. */
    const val MATCH_THRESHOLD = 0.5f

    val instagramReel = MarkerSet(
        groups = listOf(
            listOf("share"),
            listOf("repost"),
            listOf("follow"),
        ),
    )

    /**
     * Buttons on the Shorts action rail. These are not the Instagram
     * share / repost / follow set. "Like" must not also match "dislike".
     */
    val youtubeShort = MarkerSet(
        groups = listOf(
            listOf("like"),
            listOf("dislike"),
            listOf("comment"),
            listOf("remix"),
        ),
    )
}

fun isMarkerMatch(ratio: Float): Boolean = ratio >= Markers.MATCH_THRESHOLD

fun labelHasMarker(label: String, needle: String): Boolean {
    val text = label.lowercase()
    if (needle == "like" && text.contains("dislike")) return false
    return text.contains(needle)
}

fun matchRatio(labels: List<String>, markers: MarkerSet): Float {
    if (markers.groups.isEmpty()) return 0f
    val hits = markers.groups.count { group ->
        labels.any { label -> group.any { needle -> labelHasMarker(label, needle) } }
    }
    return hits.toFloat() / markers.groups.size
}

fun inBottomBand(bounds: Bounds, screen: Bounds): Boolean {
    if (screen.height <= 0) return false
    val bandTop = screen.top + (screen.height * 0.82f).toInt()
    return bounds.centerY >= bandTop
}

fun isReelsTab(label: String): Boolean {
    val normalized = label.trim().lowercase()
    return normalized == "reels" ||
        normalized.startsWith("reels,") ||
        normalized.startsWith("reels ") ||
        normalized.contains("reels tab")
}

fun isShortsTab(label: String): Boolean {
    val normalized = label.trim().lowercase()
    return normalized == "shorts" ||
        normalized.startsWith("shorts,") ||
        normalized.startsWith("shorts ") ||
        normalized.contains("shorts tab")
}

/** Covers only the bottom-bar Reels or Shorts icon. Feed rows are left alone. */
fun iconCoverRegions(
    packageName: String,
    controls: List<VisibleControl>,
    screen: Bounds,
): List<Bounds> {
    val regions = ArrayList<Bounds>()
    for (control in controls) {
        if (!inBottomBand(control.bounds, screen)) continue
        if (control.bounds.width <= 0 || control.bounds.height <= 0) continue
        val hide = when (packageName) {
            INSTAGRAM_PACKAGE -> isReelsTab(control.label)
            YOUTUBE_PACKAGE -> isShortsTab(control.label)
            else -> false
        }
        if (hide) regions.add(control.bounds)
    }
    return regions
}

/** Player containers only. A Shorts shelf on the home feed must not count. */
fun isShortsPlayerId(viewId: String): Boolean {
    val id = viewId.lowercase()
    if (id.contains("shelf") || id.contains("pivot") || id.contains("navigation")) return false
    return id.contains("reel_player") ||
        id.contains("shorts_player") ||
        id.contains("reel_watch")
}

/**
 * A Reel or Short is open when about half of that app's marker buttons sit
 * together on the action rail. YouTube also counts its Shorts player view id.
 */
fun classifyOpenedClip(
    packageName: String,
    controls: List<VisibleControl>,
    screen: Bounds,
    density: Float,
): OpenedClip? {
    if (packageName == YOUTUBE_PACKAGE && controls.any { isShortsPlayerId(it.viewId) }) {
        return OpenedClip.YOUTUBE_SHORT
    }
    val markers = when (packageName) {
        INSTAGRAM_PACKAGE -> Markers.instagramReel
        YOUTUBE_PACKAGE -> Markers.youtubeShort
        else -> return null
    }
    val candidates = controls.filter { isMarkerControl(it, screen, density) }
    if (!openedViewer(candidates, markers, screen, density)) return null
    return when (packageName) {
        INSTAGRAM_PACKAGE -> OpenedClip.INSTAGRAM_REEL
        else -> OpenedClip.YOUTUBE_SHORT
    }
}

private fun isMarkerControl(control: VisibleControl, screen: Bounds, density: Float): Boolean {
    if (control.label.length !in 1..64) return false
    if (inBottomBand(control.bounds, screen)) return false
    if (control.clickable) return true
    val maxSide = (140f * density).toInt().coerceAtLeast(1)
    return control.bounds.width in 1..maxSide && control.bounds.height in 1..maxSide
}

private fun openedViewer(
    controls: List<VisibleControl>,
    markers: MarkerSet,
    screen: Bounds,
    density: Float,
): Boolean {
    if (markers.groups.isEmpty()) return false
    val hits = markers.groups.mapNotNull { group ->
        controls
            .filter { control -> group.any { needle -> labelHasMarker(control.label, needle) } }
            .minByOrNull { screen.right - it.bounds.centerX }
    }
    val ratio = hits.size.toFloat() / markers.groups.size
    if (!isMarkerMatch(ratio)) return false
    return clusteredActionRail(hits.map { it.bounds }, screen, density)
}

fun clusteredActionRail(bounds: List<Bounds>, screen: Bounds, density: Float): Boolean {
    if (bounds.size < 2 || screen.height <= 0) return false
    val xSpread = bounds.maxOf { it.centerX } - bounds.minOf { it.centerX }
    val ySpread = bounds.maxOf { it.centerY } - bounds.minOf { it.centerY }
    val maxX = 120f * density
    val minY = 24f * density
    val maxY = screen.height * 0.6f
    return xSpread <= maxX && ySpread >= minY && ySpread <= maxY
}
