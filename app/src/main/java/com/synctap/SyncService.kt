package com.synctap

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Lớp phủ vẽ khung các vùng cửa sổ game. */
class RectView(c: Context) : View(c) {
    val rects = ArrayList<RectF>()
    var cur: RectF? = null
    private val p = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.GREEN }
    private val pc = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.YELLOW }
    override fun onDraw(c: Canvas) {
        rects.forEach { c.drawRect(it, p) }
        cur?.let { c.drawRect(it, pc) }
    }
}

class SyncService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var capture: RectView? = null
    private var capParams: WindowManager.LayoutParams? = null
    private var syncBtn: TextView? = null
    private var syncParams: WindowManager.LayoutParams? = null
    private var zoneBtn: TextView? = null
    private var zoneParams: WindowManager.LayoutParams? = null

    private var syncOn = false
    private var calibrating = false
    private var rects = ArrayList<RectF>()      // các vùng cửa sổ đã cài
    private val points = ArrayList<PointF>()
    private var downTime = 0L
    private var sx = 0f
    private var sy = 0f

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        Logger.init(applicationContext)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        log("=== Service connected | ${Build.MODEL} | Android ${Build.VERSION.SDK_INT} | screen ${dm.widthPixels}x${dm.heightPixels} ===")
        loadRects()
        log("loaded rects=${rects.map { rs(it) }} panes=${paneCount()}")
        syncBtn = makeButton("SYNC OFF", 20, 300, { toggleSync() }).also { syncParams = it.second }.first
        zoneBtn = makeButton(zoneLabel(), 20, 420, { toggleCalibrate() }).also { zoneParams = it.second }.first
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        removeCapture()
        syncBtn?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        zoneBtn?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        super.onDestroy()
    }

    // ---------- Lưu / đọc vùng ----------
    private fun saveRects() {
        val s = rects.joinToString(";") { "${it.left},${it.top},${it.right},${it.bottom}" }
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("rects", s).apply()
    }

    private fun loadRects() {
        rects.clear()
        val s = getSharedPreferences("cfg", MODE_PRIVATE).getString("rects", "") ?: ""
        if (s.isBlank()) return
        for (part in s.split(";")) {
            val v = part.split(",").mapNotNull { it.toFloatOrNull() }
            if (v.size == 4) rects.add(RectF(v[0], v[1], v[2], v[3]))
        }
    }

    private fun log(m: String) = Logger.d("SVC", m)
    private fun rs(r: RectF) = "[${r.left.toInt()},${r.top.toInt()} - ${r.right.toInt()},${r.bottom.toInt()}]"

    private fun paneCount() = getSharedPreferences("cfg", MODE_PRIVATE).getInt("panes", 2).coerceIn(1, 8)
    private fun zoneLabel() = if (rects.isEmpty()) "VÙNG: chưa cài" else "VÙNG ✓ (${rects.size})"

    // ---------- Nút nổi ----------
    private fun makeButton(label: String, x0: Int, y0: Int, onClick: () -> Unit):
        Pair<TextView, WindowManager.LayoutParams> {
        val tv = TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(28, 18, 28, 18)
            background = GradientDrawable().apply {
                cornerRadius = 40f
                setColor(Color.parseColor("#CC555555"))
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = x0; y = y0 }

        var dx0 = 0f; var dy0 = 0f; var ox = 0; var oy = 0; var moved = false
        tv.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { dx0 = e.rawX; dy0 = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - dx0; val dy = e.rawY - dy0
                    if (abs(dx) > 15 || abs(dy) > 15) moved = true
                    if (moved) { lp.x = ox + dx.toInt(); lp.y = oy + dy.toInt(); wm.updateViewLayout(tv, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onClick()
            }
            true
        }
        wm.addView(tv, lp)
        return Pair(tv, lp)
    }

    private fun setColor(tv: TextView?, hex: String) {
        (tv?.background as? GradientDrawable)?.setColor(Color.parseColor(hex))
    }

    private fun bringButtonsToTop() {
        syncBtn?.let { wm.removeView(it); wm.addView(it, syncParams) }
        zoneBtn?.let { wm.removeView(it); wm.addView(it, zoneParams) }
    }

    // ---------- Bật/tắt đồng bộ ----------
    private fun toggleSync() {
        if (calibrating) stopCalibrate(save = false)
        syncOn = !syncOn
        log("toggleSync -> ${if (syncOn) "ON" else "OFF"} (rects=${rects.size})")
        if (syncOn) {
            addCapture()
            capture?.rects?.apply { clear(); addAll(rects) }
            capture?.invalidate()
            bringButtonsToTop()
            syncBtn?.text = "SYNC ON"; setColor(syncBtn, "#CC1B9E3C")
        } else {
            removeCapture()
            syncBtn?.text = "SYNC OFF"; setColor(syncBtn, "#CC555555")
        }
    }

    // ---------- Cài vùng cửa sổ ----------
    private fun toggleCalibrate() {
        if (calibrating) { stopCalibrate(save = false); return }
        syncOn = false
        syncBtn?.text = "SYNC OFF"; setColor(syncBtn, "#CC555555")
        removeCapture()
        calibrating = true
        log("calibrate START, need ${paneCount()} rects")
        addCapture()
        capture?.rects?.clear()
        bringButtonsToTop()
        zoneBtn?.text = "VÙNG 1/${paneCount()}: kéo khung quanh cửa sổ 1"
        setColor(zoneBtn, "#CCE08A00")
    }

    private fun stopCalibrate(save: Boolean) {
        log("calibrate STOP save=$save rects=${capture?.rects?.map { rs(it) }}")
        if (save) {
            rects = ArrayList(capture?.rects ?: emptyList())
            saveRects()
        }
        calibrating = false
        removeCapture()
        zoneBtn?.text = zoneLabel(); setColor(zoneBtn, "#CC555555")
    }

    // ---------- Lớp phủ bắt chạm ----------
    private fun addCapture() {
        val v = RectView(this).apply { setBackgroundColor(Color.parseColor("#14FF0000")) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        v.setOnTouchListener { _, e ->
            if (e.pointerCount > 1) return@setOnTouchListener true
            if (calibrating) handleCalibrate(v, e) else handleSync(e)
            true
        }
        capture = v; capParams = lp
        wm.addView(v, lp)
    }

    private fun removeCapture() {
        capture?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        capture = null; capParams = null
    }

    private fun handleCalibrate(v: RectView, e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY }
            MotionEvent.ACTION_MOVE -> {
                v.cur = RectF(min(sx, e.rawX), min(sy, e.rawY), max(sx, e.rawX), max(sy, e.rawY))
                v.invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val r = RectF(min(sx, e.rawX), min(sy, e.rawY), max(sx, e.rawX), max(sy, e.rawY))
                v.cur = null
                if (r.width() > 100 && r.height() > 100) v.rects.add(r)
                log("calibrate rect ${rs(r)} accepted=${r.width() > 100 && r.height() > 100}")
                v.invalidate()
                val n = paneCount()
                if (v.rects.size >= n) stopCalibrate(save = true)
                else zoneBtn?.text = "VÙNG ${v.rects.size + 1}/$n: kéo khung quanh cửa sổ ${v.rects.size + 1}"
            }
        }
    }

    private fun handleSync(e: MotionEvent) {
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                points.clear(); downTime = e.eventTime; points.add(PointF(e.rawX, e.rawY))
                val loc = IntArray(2)
                capture?.getLocationOnScreen(loc)
                log("DOWN raw=(${e.rawX.toInt()},${e.rawY.toInt()}) local=(${e.x.toInt()},${e.y.toInt()}) overlayOrigin=(${loc[0]},${loc[1]})")
            }
            MotionEvent.ACTION_MOVE -> points.add(PointF(e.rawX, e.rawY))
            MotionEvent.ACTION_UP -> {
                points.add(PointF(e.rawX, e.rawY))
                val el = e.eventTime - downTime
                log("UP raw=(${e.rawX.toInt()},${e.rawY.toInt()}) points=${points.size} held=${el}ms")
                replay(el)
            }
        }
    }

    private fun setPassThrough(on: Boolean) {
        val v = capture ?: return
        val lp = capParams ?: return
        log("passThrough=$on")
        lp.flags = if (on) lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                   else lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    /** Danh sách vùng: dùng vùng đã cài, nếu chưa có thì chia đều màn hình. */
    private fun panes(): List<RectF> {
        if (rects.isNotEmpty()) return rects
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        val w = dm.widthPixels.toFloat(); val h = dm.heightPixels.toFloat()
        val n = paneCount()
        return (0 until n).map { i ->
            if (h >= w) RectF(0f, i * h / n, w, (i + 1) * h / n)
            else RectF(i * w / n, 0f, (i + 1) * w / n, h)
        }
    }

    // ---------- Phát lại lên mọi cửa sổ ----------
    private fun replay(elapsed: Long) {
        if (points.isEmpty()) return
        val list = panes()
        val first = points.first()
        val srcIdx = list.indexOfFirst { it.contains(first.x, first.y) }
        val src = list.getOrNull(srcIdx)

        val totalMove = abs(points.last().x - first.x) + abs(points.last().y - first.y)
        val isTap = totalMove < 12
        val duration = max(elapsed, 50L).coerceAtMost(10000L)
        log("replay panes=${list.map { rs(it) }} srcIdx=$srcIdx isTap=$isTap move=${totalMove.toInt()} duration=${duration}ms")
        val builder = GestureDescription.Builder()

        fun addStroke(name: String, mapper: (PointF) -> PointF) {
            val path = Path()
            val p0 = mapper(points[0]); path.moveTo(p0.x, p0.y)
            val l = mapper(points.last())
            if (!isTap) {
                val step = max(1, points.size / 40)
                var k = step
                while (k < points.size) { val q = mapper(points[k]); path.lineTo(q.x, q.y); k += step }
                path.lineTo(l.x, l.y)
            }
            log("  stroke $name start=(${p0.x.toInt()},${p0.y.toInt()}) end=(${l.x.toInt()},${l.y.toInt()})")
            builder.addStroke(GestureDescription.StrokeDescription(path, 0, duration))
        }

        if (src == null) {
            log("  touch ngoài mọi vùng, phát nguyên tại chỗ")
            addStroke("raw") { it }
        } else {
            list.forEachIndexed { i, dst ->
                addStroke("pane$i") { p ->
                    PointF(
                        dst.left + (p.x - src.left) / src.width() * dst.width(),
                        dst.top + (p.y - src.top) / src.height() * dst.height()
                    )
                }
            }
        }

        setPassThrough(true)
        val t0 = SystemClock.uptimeMillis()
        val ok = dispatchGesture(builder.build(), object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                log("dispatch COMPLETED sau ${SystemClock.uptimeMillis() - t0}ms")
                setPassThrough(false)
            }
            override fun onCancelled(g: GestureDescription?) {
                log("dispatch CANCELLED sau ${SystemClock.uptimeMillis() - t0}ms")
                setPassThrough(false)
            }
        }, null)
        log("dispatchGesture returned $ok")
        if (!ok) setPassThrough(false)
    }
}
