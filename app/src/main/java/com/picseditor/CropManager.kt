package com.picseditor

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class CropManager(
    private val activity: AppCompatActivity,
    private val rootView: FrameLayout,
    private val imageView: ImageView,
    private val zoomPan: ZoomPanHandler,
    private val onComplete: (Bitmap) -> Unit,
    private val onCancel: () -> Unit
) {
    enum class Mode { RATIO, FREE, AUTO }

    private val ctx: Context = activity
    private val dens = ctx.resources.displayMetrics.density
    private fun dp(v: Float) = (v * dens).toInt()
    private fun dpf(v: Float) = v * dens

    private lateinit var container: FrameLayout
    private lateinit var overlay: CropOverlayView
    private lateinit var txtSize: TextView
    private lateinit var txtCoords: TextView
    private lateinit var btnUndo: TextView
    private lateinit var panelRatio: LinearLayout
    private lateinit var panelFree: LinearLayout
    private lateinit var panelAuto: LinearLayout
    private lateinit var tabRatio: TextView
    private lateinit var tabFree: TextView
    private lateinit var tabAuto: TextView
    private lateinit var etX1: EditText
    private lateinit var etY1: EditText
    private lateinit var etX2: EditText
    private lateinit var etY2: EditText

    private var mode = Mode.RATIO
    private val undoStack = ArrayDeque<RectF>()

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() { dismiss(); onCancel() }
    }

    fun show() {
        zoomPan.init()
        imageView.post { buildUI() }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
    }

    fun dismiss() {
        backCallback.isEnabled = false
        zoomPan.onMatrixChanged = null
        if (::container.isInitialized) rootView.removeView(container)
    }

    private fun buildUI() {
        val sbRes = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarH = if (sbRes > 0) ctx.resources.getDimensionPixelSize(sbRes) else 0
        val nbRes = ctx.resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navBarH = if (nbRes > 0) ctx.resources.getDimensionPixelSize(nbRes) else 0

        container = FrameLayout(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }

        overlay = CropOverlayView(ctx, imageView, zoomPan).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        val bmp = bitmap() ?: return
        overlay.setCrop(RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat()))
        overlay.onChanged = { rect -> onOverlayChanged(rect) }
        overlay.onDragEnd = { pushUndo() }
        pushUndo()

        zoomPan.onMatrixChanged = { overlay.invalidate() }

        container.addView(overlay)
        container.addView(buildTopBar(statusBarH))
        container.addView(buildBottomPanel(navBarH))
        rootView.addView(container)
        updateInfo(overlay.cropBitmap)
    }

    private fun buildTopBar(statusBarH: Int): LinearLayout {
        val bar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xF0141414.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(52f) + statusBarH
            ).apply { gravity = Gravity.TOP }
            setPadding(dp(4f), statusBarH, dp(4f), 0)
        }
        bar.addView(makeTextBtn("✕") { dismiss(); onCancel() })
        val tabRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        tabRatio = makeTab("Ratio", true)  { switchMode(Mode.RATIO) }
        tabFree  = makeTab("Free",  false) { switchMode(Mode.FREE) }
        tabAuto  = makeTab("Auto",  false) { switchMode(Mode.AUTO) }
        tabRow.addView(tabRatio); tabRow.addView(spacerH(6))
        tabRow.addView(tabFree);  tabRow.addView(spacerH(6))
        tabRow.addView(tabAuto)
        bar.addView(tabRow)
        bar.addView(makeTextBtn("✓") { applyAndComplete() }.apply { setTextColor(0xFF66BB6A.toInt()) })
        return bar
    }

    private fun buildBottomPanel(navBarH: Int): LinearLayout {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF0141414.toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }
            setPadding(dp(12f), dp(8f), dp(12f), dp(12f) + navBarH)
        }
        val infoRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28f))
        }
        txtSize = infoLabel("W: ---  H: ---")
        txtCoords = infoLabel("").apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnUndo = TextView(ctx).apply {
            text = "↩"; textSize = 18f; setTextColor(0xFF444444.toInt())
            setPadding(dp(8f), 0, 0, 0)
            setOnClickListener { undo() }
        }
        infoRow.addView(txtSize); infoRow.addView(txtCoords); infoRow.addView(btnUndo)
        panel.addView(infoRow)
        panel.addView(View(ctx).apply {
            setBackgroundColor(0xFF222222.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1).apply {
                setMargins(0, dp(6f), 0, dp(6f))
            }
        })
        panelRatio = buildRatioPanel()
        panelFree  = buildFreePanel()
        panelAuto  = buildAutoPanel()
        panelFree.visibility = View.GONE
        panelAuto.visibility = View.GONE
        panel.addView(panelRatio); panel.addView(panelFree); panel.addView(panelAuto)
        return panel
    }

    private fun buildRatioPanel(): LinearLayout {
        val ratios = listOf("Free" to null, "1:1" to 1f, "4:3" to 4f/3, "3:2" to 3f/2,
                            "16:9" to 16f/9, "9:16" to 9f/16, "3:4" to 3f/4, "2:3" to 2f/3)
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(2f), 0, dp(2f)) }
        ratios.forEach { (label, ratio) ->
            content.addView(makeChip(label) { applyRatio(ratio) })
            content.addView(spacerH(6))
        }
        val scroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(content)
        }
        row.addView(scroll); row.addView(spacerH(8))
        val etW = numInput("W", dp(36f)); val etH = numInput("H", dp(36f))
        row.addView(etW)
        row.addView(TextView(ctx).apply { text = ":"; setTextColor(0xFF666666.toInt()); setPadding(dp(4f),0,dp(4f),0) })
        row.addView(etH); row.addView(spacerH(4))
        row.addView(makeChip("OK") {
            val rw = etW.text.toString().toFloatOrNull() ?: return@makeChip
            val rh = etH.text.toString().toFloatOrNull() ?: return@makeChip
            if (rh > 0) applyRatio(rw / rh)
        })
        return row
    }

    private fun buildFreePanel(): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        etX1 = coordInput("X1"); etY1 = coordInput("Y1")
        etX2 = coordInput("X2"); etY2 = coordInput("Y2")
        listOf(etX1, etY1, etX2, etY2).forEach {
            row.addView(it); row.addView(spacerH(4))
            it.setOnEditorActionListener { _, _, _ -> applyFreeCoords(); true }
        }
        row.addView(makeChip("OK") { applyFreeCoords() })
        return row
    }

    private fun buildAutoPanel(): LinearLayout {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        var threshold = 30
        val lbl = infoLabel("สีดำ < $threshold")
        val seek = SeekBar(ctx).apply {
            max = 100; progress = threshold
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) { threshold = v; lbl.text = "สีดำ < $v" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        row.addView(lbl); row.addView(spacerH(8)); row.addView(seek); row.addView(spacerH(8))
        row.addView(makeChip("Detect") { autoDetect(threshold) })
        return row
    }

    private fun switchMode(newMode: Mode) {
        mode = newMode
        overlay.lockedRatio = null
        panelRatio.visibility = if (mode == Mode.RATIO) View.VISIBLE else View.GONE
        panelFree.visibility  = if (mode == Mode.FREE)  View.VISIBLE else View.GONE
        panelAuto.visibility  = if (mode == Mode.AUTO)  View.VISIBLE else View.GONE
        listOf(tabRatio to Mode.RATIO, tabFree to Mode.FREE, tabAuto to Mode.AUTO).forEach { (tab, m) ->
            val a = m == mode
            tab.setTextColor(if (a) Color.BLACK else 0xFFAAAAAA.toInt())
            (tab.background as? GradientDrawable)?.setColor(if (a) Color.WHITE else Color.TRANSPARENT)
        }
    }

    private fun applyRatio(ratio: Float?) {
        if (ratio == null) { overlay.lockedRatio = null; return }
        val bmp = bitmap() ?: return
        val cw = overlay.cropBitmap.width()
        val rect = RectF(overlay.cropBitmap.left, overlay.cropBitmap.top,
                         overlay.cropBitmap.left + cw, overlay.cropBitmap.top + cw / ratio)
        if (rect.bottom > bmp.height) {
            rect.bottom = bmp.height.toFloat()
            rect.right = (rect.left + rect.height() * ratio).coerceAtMost(bmp.width.toFloat())
        }
        overlay.lockedRatio = ratio
        overlay.setCrop(rect); updateInfo(rect); pushUndo()
    }

    private fun applyFreeCoords() {
        val bmp = bitmap() ?: return
        val x1 = etX1.text.toString().toFloatOrNull()?.coerceIn(0f, bmp.width.toFloat()) ?: return
        val y1 = etY1.text.toString().toFloatOrNull()?.coerceIn(0f, bmp.height.toFloat()) ?: return
        val x2 = etX2.text.toString().toFloatOrNull()?.coerceIn(0f, bmp.width.toFloat()) ?: return
        val y2 = etY2.text.toString().toFloatOrNull()?.coerceIn(0f, bmp.height.toFloat()) ?: return
        if (x2 <= x1 || y2 <= y1) return
        val rect = RectF(x1, y1, x2, y2)
        overlay.setCrop(rect); updateInfo(rect); pushUndo()
    }

    private fun autoDetect(threshold: Int) {
        val bmp = bitmap() ?: return
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        fun isBlack(p: Int) = Color.red(p) < threshold && Color.green(p) < threshold && Color.blue(p) < threshold
        var top = 0; var bottom = bmp.height - 1; var left = 0; var right = bmp.width - 1
        outer@ for (y in 0 until bmp.height) for (x in 0 until bmp.width) if (!isBlack(px[y*bmp.width+x])) { top = y; break@outer }
        outer@ for (y in bmp.height-1 downTo 0) for (x in 0 until bmp.width) if (!isBlack(px[y*bmp.width+x])) { bottom = y; break@outer }
        outer@ for (x in 0 until bmp.width) for (y in 0 until bmp.height) if (!isBlack(px[y*bmp.width+x])) { left = x; break@outer }
        outer@ for (x in bmp.width-1 downTo 0) for (y in 0 until bmp.height) if (!isBlack(px[y*bmp.width+x])) { right = x; break@outer }
        val rect = RectF(left.toFloat(), top.toFloat(), (right+1).toFloat(), (bottom+1).toFloat())
        overlay.setCrop(rect); updateInfo(rect); pushUndo()
    }

    private fun onOverlayChanged(rect: RectF) {
        updateInfo(rect)
        if (mode == Mode.FREE && ::etX1.isInitialized) {
            etX1.setText(rect.left.toInt().toString())
            etY1.setText(rect.top.toInt().toString())
            etX2.setText(rect.right.toInt().toString())
            etY2.setText(rect.bottom.toInt().toString())
        }
    }

    private fun updateInfo(rect: RectF) {
        txtSize.text = "W: ${rect.width().toInt()}  H: ${rect.height().toInt()}"
        txtCoords.text = "  ·  X: ${rect.left.toInt()}–${rect.right.toInt()}  Y: ${rect.top.toInt()}–${rect.bottom.toInt()}"
        if (::btnUndo.isInitialized) btnUndo.setTextColor(if (undoStack.size > 1) Color.WHITE else 0xFF444444.toInt())
    }

    private fun pushUndo() {
        undoStack.addLast(RectF(overlay.cropBitmap))
        if (::btnUndo.isInitialized) btnUndo.setTextColor(if (undoStack.size > 1) Color.WHITE else 0xFF444444.toInt())
    }

    private fun undo() {
        if (undoStack.size <= 1) return
        undoStack.removeLast()
        val prev = undoStack.last()
        overlay.setCrop(prev); updateInfo(prev)
    }

    private fun applyAndComplete() {
        val bmp = bitmap() ?: return
        val r = overlay.cropBitmap
        val x = r.left.toInt().coerceIn(0, bmp.width-1)
        val y = r.top.toInt().coerceIn(0, bmp.height-1)
        val w = r.width().toInt().coerceAtMost(bmp.width-x).coerceAtLeast(1)
        val h = r.height().toInt().coerceAtMost(bmp.height-y).coerceAtLeast(1)
        val cropped = Bitmap.createBitmap(bmp, x, y, w, h)
        dismiss(); onComplete(cropped)
    }

    private fun bitmap() = (imageView.drawable as? BitmapDrawable)?.bitmap

    private fun makeTextBtn(text: String, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text; textSize = 20f; setTextColor(Color.WHITE)
        gravity = Gravity.CENTER; setPadding(dp(14f), dp(8f), dp(14f), dp(8f))
        setOnClickListener { onClick() }
    }

    private fun makeTab(label: String, active: Boolean, onClick: () -> Unit): TextView {
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dpf(20f)
            setColor(if (active) Color.WHITE else Color.TRANSPARENT)
            if (!active) setStroke(1, 0xFF444444.toInt())
        }
        return TextView(ctx).apply {
            text = label; textSize = 12f; gravity = Gravity.CENTER
            setPadding(dp(14f), dp(5f), dp(14f), dp(5f))
            setTextColor(if (active) Color.BLACK else 0xFFAAAAAA.toInt())
            background = gd; setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(32f))
        }
    }

    private fun makeChip(label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label; textSize = 12f; setTextColor(0xFFDDDDDD.toInt())
        setPadding(dp(10f), dp(5f), dp(10f), dp(5f))
        background = GradientDrawable().apply { setColor(0xFF252525.toInt()); cornerRadius = dpf(6f); setStroke(1, 0xFF333333.toInt()) }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun infoLabel(text: String) = TextView(ctx).apply {
        this.text = text; textSize = 12f; setTextColor(0xFFAAAAAA.toInt())
    }

    private fun numInput(hint: String, width: Int) = EditText(ctx).apply {
        this.hint = hint; textSize = 12f; setTextColor(Color.WHITE); setHintTextColor(0xFF444444.toInt())
        background = GradientDrawable().apply { setColor(0xFF252525.toInt()); cornerRadius = dpf(4f) }
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER; setPadding(dp(4f), 0, dp(4f), 0)
        layoutParams = LinearLayout.LayoutParams(width, dp(32f))
    }

    private fun coordInput(hint: String) = EditText(ctx).apply {
        this.hint = hint; textSize = 11f; setTextColor(Color.WHITE); setHintTextColor(0xFF444444.toInt())
        background = GradientDrawable().apply { setColor(0xFF252525.toInt()); cornerRadius = dpf(4f) }
        inputType = android.text.InputType.TYPE_CLASS_NUMBER
        gravity = Gravity.CENTER; setPadding(dp(3f), 0, dp(3f), 0)
        layoutParams = LinearLayout.LayoutParams(dp(52f), dp(32f))
    }

    private fun spacerH(dpVal: Int) = Space(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(dp(dpVal.toFloat()), 1)
    }
}
