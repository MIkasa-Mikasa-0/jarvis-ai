package com.jarvis.ai.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object { var inst: JarvisAccessibilityService? = null }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onServiceConnected() { inst = this }
    override fun onUnbind(intent: Intent?): Boolean { inst = null; return super.onUnbind(intent) }

    fun readScreen(): String {
        val root = rootInActiveWindow ?: return "Cannot read the screen right now (no active window)."
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo, d: Int) {
            n.text?.toString()?.takeIf { it.isNotBlank() }?.let { sb.append("  ".repeat(d.coerceAtMost(3))).append(it).append('\n') }
            for (i in 0 until n.childCount.coerceAtMost(40)) n.getChild(i)?.let { walk(it, d + 1) }
        }
        walk(root, 0)
        return if (sb.isEmpty()) "Screen has no readable text." else sb.toString().take(1500)
    }

    fun tap(x: Float, y: Float): Boolean {
        val p = Path(); p.moveTo(x, y)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 80)).build()
        return dispatchGesture(g, null, null)
    }

    fun tapLabel(label: String): String {
        val node = find(rootInActiveWindow, label) ?: return "Could not find '$label' on screen."
        val r = Rect(); node.getBoundsInScreen(r); node.recycle()
        return if (tap(r.centerX().toFloat(), r.centerY().toFloat()))
            "Tapped '$label' at (${r.centerX()}, ${r.centerY()})."
        else "Failed to dispatch tap gesture."
    }

    fun typeIntoFocus(text: String): String {
        val root = rootInActiveWindow ?: return "No active window."
        val focus = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return "No focused input field found. Tap a text field first."
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        return if (focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Typed: $text"
        else "The focused field refused text input."
    }

    fun scrollDown(): String {
        val root = rootInActiveWindow ?: return "No active window."
        val r = Rect(); root.getBoundsInScreen(r)
        val p = Path()
        p.moveTo(r.centerX().toFloat(), r.height() * 0.75f)
        p.lineTo(r.centerX().toFloat(), r.height() * 0.25f)
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, 350)).build()
        return if (dispatchGesture(g, null, null)) "Scrolled down." else "Scroll gesture failed."
    }

    fun pressBack(): String { performGlobalAction(GLOBAL_ACTION_BACK); return "Pressed back." }

    private fun find(root: AccessibilityNodeInfo?, label: String): AccessibilityNodeInfo? {
        root ?: return null
        val hit = root.text?.toString()?.contains(label, true) == true ||
            root.contentDescription?.toString()?.contains(label, true) == true
        if (hit) return root
        for (i in 0 until root.childCount) {
            val c = root.getChild(i) ?: continue
            val f = find(c, label)
            if (f != null) return f
        }
        return null
    }
}
