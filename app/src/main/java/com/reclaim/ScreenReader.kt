package com.reclaim

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.reclaim.logic.Bounds
import com.reclaim.logic.VisibleControl

object ScreenReader {
    fun controlsFrom(root: AccessibilityNodeInfo): List<VisibleControl> {
        val controls = ArrayList<VisibleControl>()
        walk(root, controls)
        return controls
    }

    fun labelOf(node: AccessibilityNodeInfo): String {
        return listOfNotNull(node.contentDescription?.toString(), node.text?.toString())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" ")
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<VisibleControl>) {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val label = labelOf(node)
        val viewId = node.viewIdResourceName.orEmpty()
        if (rect.width() > 0 && rect.height() > 0 && (label.isNotEmpty() || viewId.isNotEmpty())) {
            out.add(
                VisibleControl(
                    label = label,
                    bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                    clickable = node.isClickable,
                    viewId = viewId,
                ),
            )
        }
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                walk(child, out)
            } finally {
                child.recycle()
            }
        }
    }
}
