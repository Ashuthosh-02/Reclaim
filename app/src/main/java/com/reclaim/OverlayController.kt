package com.reclaim

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Configuration
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.reclaim.logic.Bounds
import kotlin.math.hypot

/**
 * Covers the bottom-bar Reels or Shorts icon only.
 * Feed rows are not covered. A separate layer watches swipes on an open Reel or Short.
 * That layer does not take key focus, so the system back button still reaches the app.
 */
class OverlayController(
    private val service: AccessibilityService,
    private val onSwipe: () -> Unit,
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val iconCovers = mutableListOf<View>()
    private var iconRegions: List<Bounds> = emptyList()
    private var guard: SwipeCatcherView? = null

    fun updateIcons(regions: List<Bounds>) {
        if (regions == iconRegions) return
        iconRegions = regions
        clearIcons()
        val color = coverColor()
        for (region in regions) {
            if (region.width < 4 || region.height < 4) continue
            val view = View(service).apply {
                setBackgroundColor(color)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                isFocusable = false
            }
            try {
                windowManager.addView(view, iconParams(region))
                iconCovers.add(view)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not cover an icon", error)
            }
        }
    }

    fun setSwipeGuard(enabled: Boolean) {
        if (enabled) {
            if (guard != null) return
            val view = SwipeCatcherView(service, onSwipe, ::forwardTap)
            try {
                windowManager.addView(view, guardParams())
                guard = view
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not watch swipes", error)
            }
        } else {
            guard?.let { view ->
                runCatching { windowManager.removeView(view) }
            }
            guard = null
        }
    }

    fun clear() {
        iconRegions = emptyList()
        clearIcons()
        setSwipeGuard(false)
    }

    private fun clearIcons() {
        iconCovers.forEach { view -> runCatching { windowManager.removeView(view) } }
        iconCovers.clear()
    }

    private fun coverColor(): Int {
        val nightMode = service.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (nightMode == Configuration.UI_MODE_NIGHT_YES) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    private fun iconParams(region: Bounds): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            region.width,
            region.height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = region.left
            y = region.top
        }
    }

    private fun forwardTap(x: Float, y: Float) {
        val view = guard ?: return
        setGuardTouchable(view, false)
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    restoreGuard()
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    restoreGuard()
                }
            },
            null,
        )
        if (!dispatched) restoreGuard()
    }

    private fun restoreGuard() {
        handler.postDelayed({
            guard?.let { setGuardTouchable(it, true) }
        }, 80)
    }

    private fun setGuardTouchable(view: View, touchable: Boolean) {
        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val notTouchable = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (touchable) {
            params.flags and notTouchable.inv()
        } else {
            params.flags or notTouchable
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun guardParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    private class SwipeCatcherView(
        service: AccessibilityService,
        private val onSwipe: () -> Unit,
        private val onTap: (Float, Float) -> Unit,
    ) : View(service) {
        private var downX = 0f
        private var downY = 0f
        private var swiped = false
        private val swipeSlop = 48f * resources.displayMetrics.density

        init {
            isFocusable = false
            isFocusableInTouchMode = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    swiped = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!swiped && hypot(event.rawX - downX, event.rawY - downY) >= swipeSlop) {
                        swiped = true
                        onSwipe()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!swiped) onTap(event.rawX, event.rawY)
                }
            }
            return true
        }
    }

    companion object {
        private const val TAG = "Reclaim"
    }
}
