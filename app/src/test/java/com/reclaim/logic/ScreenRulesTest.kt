package com.reclaim.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenRulesTest {
    private val screen = Bounds(0, 0, 1080, 2400)
    private val density = 2.5f

    @Test
    fun twoOfThreeReelMarkersOnTheActionRailClassifyAsAReel() {
        val controls = listOf(
            rail("Share", 900),
            rail("Repost", 1040),
            rail("Follow", 1180),
        )
        assertEquals(
            OpenedClip.INSTAGRAM_REEL,
            classifyOpenedClip(INSTAGRAM_PACKAGE, controls, screen, density),
        )
        assertTrue(isMarkerMatch(matchRatio(listOf("Share", "Follow"), Markers.instagramReel)))
    }

    @Test
    fun oneReelMarkerDoesNotClassify() {
        val controls = listOf(rail("Share", 900), rail("Like", 1040))
        assertNull(classifyOpenedClip(INSTAGRAM_PACKAGE, controls, screen, density))
        assertFalse(isMarkerMatch(matchRatio(listOf("Share"), Markers.instagramReel)))
    }

    @Test
    fun shareAndFollowSpreadDownTheFeedAreNotAnOpenedReel() {
        val controls = listOf(
            VisibleControl("Share", Bounds(80, 400, 180, 480), clickable = true),
            VisibleControl("Follow", Bounds(80, 1900, 180, 1980), clickable = true),
        )
        assertNull(classifyOpenedClip(INSTAGRAM_PACKAGE, controls, screen, density))
    }

    @Test
    fun instagramButtonsDoNotClassifyAYouTubeScreen() {
        val controls = listOf(rail("Share", 900), rail("Repost", 1040), rail("Follow", 1180))
        assertNull(classifyOpenedClip(YOUTUBE_PACKAGE, controls, screen, density))
    }

    @Test
    fun likeAndDislikeStackedOnTheRightClassifyAsAShort() {
        val controls = listOf(
            rail("Like this video", 900),
            rail("Dislike this video", 1060),
            rail("Comments", 1220),
        )
        assertEquals(
            OpenedClip.YOUTUBE_SHORT,
            classifyOpenedClip(YOUTUBE_PACKAGE, controls, screen, density),
        )
        assertFalse(labelHasMarker("Dislike this video", "like"))
        assertTrue(labelHasMarker("Like this video", "like"))
    }

    @Test
    fun horizontalLikeRowOnANormalVideoIsNotAShort() {
        val controls = listOf(
            VisibleControl("Like this video", Bounds(40, 900, 180, 980), clickable = true),
            VisibleControl("Dislike this video", Bounds(200, 900, 340, 980), clickable = true),
            VisibleControl("Comments", Bounds(360, 900, 500, 980), clickable = true),
        )
        assertNull(classifyOpenedClip(YOUTUBE_PACKAGE, controls, screen, density))
    }

    @Test
    fun shortsPlayerViewIdClassifiesEvenWithoutTheOldLabels() {
        val controls = listOf(
            VisibleControl(
                label = "",
                bounds = Bounds(0, 0, 1080, 2400),
                clickable = false,
                viewId = "com.google.android.youtube:id/reel_player_page_container",
            ),
        )
        assertEquals(
            OpenedClip.YOUTUBE_SHORT,
            classifyOpenedClip(YOUTUBE_PACKAGE, controls, screen, density),
        )
    }

    @Test
    fun shortsShelfOnTheHomeFeedIsNotAShort() {
        val controls = listOf(
            VisibleControl(
                label = "Shorts",
                bounds = Bounds(0, 400, 1080, 900),
                clickable = false,
                viewId = "com.google.android.youtube:id/shorts_shelf",
            ),
        )
        assertNull(classifyOpenedClip(YOUTUBE_PACKAGE, controls, screen, density))
        assertFalse(isShortsPlayerId("com.google.android.youtube:id/shorts_shelf"))
    }

    @Test
    fun bottomBarIconsAreCoveredAndFeedRowsAreNot() {
        val controls = listOf(
            VisibleControl("Shorts", Bounds(40, 500, 220, 560), clickable = false),
            VisibleControl("Reels", Bounds(40, 700, 400, 1100), clickable = true),
            VisibleControl("Home", Bounds(0, 2200, 200, 2350), clickable = true),
            VisibleControl("Reels", Bounds(220, 2200, 420, 2350), clickable = true),
            VisibleControl("Shorts", Bounds(430, 2200, 640, 2350), clickable = true),
        )
        assertEquals(
            listOf(Bounds(220, 2200, 420, 2350)),
            iconCoverRegions(INSTAGRAM_PACKAGE, controls, screen),
        )
        assertEquals(
            listOf(Bounds(430, 2200, 640, 2350)),
            iconCoverRegions(YOUTUBE_PACKAGE, controls, screen),
        )
    }

    @Test
    fun exactlyHalfOfATwoMarkerSetCountsAsAMatch() {
        assertTrue(isMarkerMatch(matchRatio(listOf("alpha"), MarkerSet(listOf(listOf("alpha"), listOf("beta"))))))
        assertFalse(isMarkerMatch(0.49f))
    }

    private fun rail(label: String, top: Int): VisibleControl {
        return VisibleControl(label, Bounds(900, top, 1040, top + 80), clickable = true)
    }
}
