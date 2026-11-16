package com.reclaim

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.reclaim.logic.Bounds
import com.reclaim.logic.INSTAGRAM_PACKAGE
import com.reclaim.logic.YOUTUBE_PACKAGE
import com.reclaim.logic.classifyOpenedClip
import com.reclaim.logic.iconCoverRegions

/**
 * Watches Instagram and YouTube.
 * A finger swipe on a detected Reel or Short presses the system back button.
 * The feed is left uncovered.
 */
class ReclaimAccessibilityService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlays: OverlayController
    private var scanQueued = false
    private var lastBack = 0L

    override fun onServiceConnected() {
        overlays = OverlayController(this, ::goBack)
        scheduleScan()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) return
        scheduleScan()
    }

    override fun onInterrupt() {
        if (::overlays.isInitialized) overlays.clear()
    }

    override fun onDestroy() {
        if (::overlays.isInitialized) overlays.clear()
        super.onDestroy()
    }

    private fun scheduleScan() {
        if (scanQueued) return
        scanQueued = true
        handler.postDelayed({
            scanQueued = false
            scan()
        }, 280)
    }

    private fun scan() {
        if (!::overlays.isInitialized) return
        val root = rootInActiveWindow
        if (root == null) {
            overlays.clear()
            return
        }
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName != INSTAGRAM_PACKAGE && packageName != YOUTUBE_PACKAGE) {
            root.recycle()
            overlays.clear()
            return
        }
        val controls = try {
            ScreenReader.controlsFrom(root)
        } finally {
            root.recycle()
        }
        val screen = screenBounds()
        overlays.updateIcons(iconCoverRegions(packageName, controls, screen))
        val opened = classifyOpenedClip(
            packageName,
            controls,
            screen,
            resources.displayMetrics.density,
        )
        overlays.setSwipeGuard(opened != null)
    }

    private fun goBack() {
        val now = SystemClock.uptimeMillis()
        if (now - lastBack < 700) return
        lastBack = now
        overlays.setSwipeGuard(false)
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun screenBounds(): Bounds {
        val windowManager = getSystemService(WindowManager::class.java)
        val rect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val metrics = resources.displayMetrics
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        return Bounds(rect.left, rect.top, rect.right, rect.bottom)
    }
}
