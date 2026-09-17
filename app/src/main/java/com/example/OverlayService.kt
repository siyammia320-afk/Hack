package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class OverlayService : Service() {

  companion object {
    const val CHANNEL_ID = "floating_overlay_channel"
    const val NOTIFICATION_ID = 101
    var isRunning = false
  }

  private var windowManager: WindowManager? = null
  private var floatingRootView: FrameLayout? = null
  private var floatingIconView: FrameLayout? = null
  private var dialogCardView: LinearLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null

  private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    isRunning = true
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, createNotification())
    initOverlayView()
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Floating Service",
        NotificationManager.IMPORTANCE_LOW
      ).apply {
        description = "Floating overlay active"
      }
      val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      manager.createNotificationChannel(channel)
    }
  }

  private fun createNotification(): Notification {
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setContentTitle("Data Transfer Overlay")
      .setContentText("ফ্লোটিং বাটন চালু আছে")
      .setSmallIcon(R.mipmap.ic_launcher)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .build()
  }

  @SuppressLint("ClickableViewAccessibility")
  private fun initOverlayView() {
    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      layoutFlag,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 50
      y = 300
    }
    layoutParams = params

    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    floatingRootView = FrameLayout(this)

    // 1. Floating Circular Icon
    val iconSize = dp(56)
    floatingIconView = FrameLayout(this).apply {
      layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
      val bg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#00796B"))
        setStroke(dp(2), Color.parseColor("#FFFFFF"))
      }
      background = bg
      elevation = dp(8).toFloat()

      val icon = ImageView(this@OverlayService).apply {
        val p = dp(14)
        setPadding(p, p, p, p)
        setImageResource(android.R.drawable.ic_menu_upload)
        setColorFilter(Color.WHITE)
      }
      addView(icon, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // 2. Dialog Window
    dialogCardView = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      visibility = View.GONE
      val p = dp(16)
      setPadding(p, p, p, p)
      val bg = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(Color.parseColor("#1E2228"))
        setStroke(dp(1), Color.parseColor("#374151"))
      }
      background = bg
      elevation = dp(12).toFloat()
      layoutParams = FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT)

      // Top Row (Title + Close)
      val topRow = LinearLayout(this@OverlayService).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this@OverlayService).apply {
          text = "Download"
          setTextColor(Color.WHITE)
          textSize = 18f
          paint.isFakeBoldText = true
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this@OverlayService).apply {
          text = "✕"
          setTextColor(Color.parseColor("#9CA3AF"))
          textSize = 18f
          setPadding(dp(8), dp(4), dp(8), dp(4))
          setOnClickListener {
            showFloatingIcon()
          }
        }
        addView(title)
        addView(closeBtn)
      }
      addView(topRow)

      // Target path hint
      val targetHint = TextView(this@OverlayService).apply {
        text = "টার্গেট: com.arafat.com"
        setTextColor(Color.parseColor("#9CA3AF"))
        textSize = 12f
        setPadding(0, dp(4), 0, dp(12))
      }
      addView(targetHint)

      // Status text
      val statusText = TextView(this@OverlayService).apply {
        text = "প্রস্তুত"
        setTextColor(Color.parseColor("#38BDF8"))
        textSize = 13f
        setPadding(0, 0, 0, dp(12))
      }

      // Download Switch Row
      val switchRow = LinearLayout(this@OverlayService).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val switchLabel = TextView(this@OverlayService).apply {
          text = "Download"
          setTextColor(Color.WHITE)
          textSize = 16f
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val downloadSwitch = Switch(this@OverlayService).apply {
          isChecked = false
          setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
              statusText.text = "ফাইল কপি হচ্ছে..."
              statusText.setTextColor(Color.parseColor("#FBBF24"))
              serviceScope.launch {
                val result = withContext(Dispatchers.IO) {
                  ShizukuManager.copyFilesToTarget(applicationContext)
                }
                if (result.first) {
                  statusText.text = result.second
                  statusText.setTextColor(Color.parseColor("#4ADE80"))
                  Toast.makeText(applicationContext, result.second, Toast.LENGTH_LONG).show()
                } else {
                  statusText.text = result.second
                  statusText.setTextColor(Color.parseColor("#F87171"))
                  Toast.makeText(applicationContext, result.second, Toast.LENGTH_LONG).show()
                }
                // Reset switch after 2 seconds
                kotlinx.coroutines.delay(2000)
                this@apply.isChecked = false
              }
            }
          }
        }
        addView(switchLabel)
        addView(downloadSwitch)
      }
      addView(switchRow)
      addView(statusText)

      // Bottom Row: Close Overlay Button
      val stopButton = TextView(this@OverlayService).apply {
        text = "ফ্লোটিং বাটন বন্ধ করুন"
        setTextColor(Color.parseColor("#EF4444"))
        textSize = 13f
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(4))
        setOnClickListener {
          stopSelf()
        }
      }
      addView(stopButton)
    }

    floatingRootView?.addView(floatingIconView)
    floatingRootView?.addView(dialogCardView)

    // Drag and Click Listener for Floating Icon
    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f

    floatingIconView?.setOnTouchListener { _, event ->
      when (event.action) {
        MotionEvent.ACTION_DOWN -> {
          initialX = params.x
          initialY = params.y
          initialTouchX = event.rawX
          initialTouchY = event.rawY
          true
        }
        MotionEvent.ACTION_MOVE -> {
          params.x = initialX + (event.rawX - initialTouchX).toInt()
          params.y = initialY + (event.rawY - initialTouchY).toInt()
          windowManager?.updateViewLayout(floatingRootView, params)
          true
        }
        MotionEvent.ACTION_UP -> {
          val diffX = abs(event.rawX - initialTouchX)
          val diffY = abs(event.rawY - initialTouchY)
          if (diffX < 10 && diffY < 10) {
            showDialogCard()
          }
          true
        }
        else -> false
      }
    }

    try {
      windowManager?.addView(floatingRootView, layoutParams)
    } catch (e: Exception) {
      Toast.makeText(this, "Overlay Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  private fun showDialogCard() {
    floatingIconView?.visibility = View.GONE
    dialogCardView?.visibility = View.VISIBLE
  }

  private fun showFloatingIcon() {
    dialogCardView?.visibility = View.GONE
    floatingIconView?.visibility = View.VISIBLE
  }

  override fun onDestroy() {
    super.onDestroy()
    isRunning = false
    try {
      floatingRootView?.let { windowManager?.removeView(it) }
    } catch (_: Exception) {}
  }
}
