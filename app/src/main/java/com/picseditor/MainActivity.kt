package com.picseditor

import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import android.os.Bundle
import android.view.WindowManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView

class MainActivity : AppCompatActivity() {

    private lateinit var canvas: ImageView
    private lateinit var zoomPan: ZoomPanHandler
    private var cropManager: CropManager? = null
    private val bitmapHistory = ArrayDeque<Bitmap>()

    private val pickImage = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val bitmap = android.graphics.BitmapFactory.decodeStream(contentResolver.openInputStream(it))
            showMain()
            canvas.setImageBitmap(bitmap)
            zoomPan.init()
            bitmapHistory.clear()
        }
    }

    private val mainBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { showStart() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        setContentView(R.layout.activity_main)

        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        val navbarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        findViewById<View>(R.id.Main).setPadding(0, 0, 0, navbarHeight)

        canvas = findViewById(R.id.canvas)
        zoomPan = ZoomPanHandler(this, canvas)
        onBackPressedDispatcher.addCallback(this, mainBackCallback)

        findViewById<View>(R.id.display).setOnTouchListener { _, event -> zoomPan.onTouch(event) }
        findViewById<View>(R.id.btnImport).setOnClickListener { pickImage.launch("image/*") }

        findViewById<View>(R.id.Crop).setOnClickListener {
            hideToolbar()
            val rootView = findViewById<FrameLayout>(android.R.id.content)
            cropManager = CropManager(
                activity = this,
                rootView = rootView,
                imageView = canvas,
                zoomPan = zoomPan,
                onComplete = { cropped ->
                    (canvas.drawable as? BitmapDrawable)?.bitmap?.let { bitmapHistory.addLast(it) }
                    canvas.setImageBitmap(cropped)
                    zoomPan.init()
                    showToolbar()
                    cropManager = null
                },
                onCancel = {
                    showToolbar()
                    cropManager = null
                }
            )
            cropManager!!.show()
        }
    }

    fun undoLastCrop() {
        if (bitmapHistory.isEmpty()) return
        val prev = bitmapHistory.removeLast()
        canvas.setImageBitmap(prev)
        zoomPan.init()
    }

    private fun showMain() {
        findViewById<View>(R.id.Start).visibility = View.GONE
        findViewById<View>(R.id.Main).visibility = View.VISIBLE
        mainBackCallback.isEnabled = true
    }

    private fun showStart() {
        findViewById<View>(R.id.Main).visibility = View.GONE
        findViewById<View>(R.id.Start).visibility = View.VISIBLE
        mainBackCallback.isEnabled = false
    }

    private fun hideToolbar() {
        val t = findViewById<View>(R.id.toolbar)
        t.animate().alpha(0f).translationY(t.height.toFloat()).setDuration(180).withEndAction { t.visibility = View.GONE }.start()
    }

    private fun showToolbar() {
        val t = findViewById<View>(R.id.toolbar)
        t.visibility = View.VISIBLE
        t.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }
}
