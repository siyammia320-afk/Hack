package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * কমপ্যাক্ট সাইজের কাস্টম সাইবার হ্যাকার ডায়ালগ লেআউট।
 * এটি উচ্চতা (Height) কখনোই ফুল স্ক্রিন করবে না, ভেতরের আইটেম অনুযায়ী টাইট ও ছোট থাকবে।
 */
class CyberHackerDialogLayout(context: Context) : LinearLayout(context) {
  private val linePaint = Paint().apply {
    style = Paint.Style.STROKE
    isAntiAlias = true
  }

  private val bgPaint = Paint().apply {
    style = Paint.Style.FILL
    color = Color.parseColor("#F4070D09")
  }

  private val borderPaint = Paint().apply {
    style = Paint.Style.STROKE
    strokeWidth = 3f
    color = Color.parseColor("#00FF66")
    isAntiAlias = true
  }

  // অটো-কালার চেঞ্জিং প্যালেট
  private val cyberColors = intArrayOf(
    Color.parseColor("#00FF66"), // Neon Green
    Color.parseColor("#00F0FF"), // Neon Cyan
    Color.parseColor("#FF007F"), // Neon Pink
    Color.parseColor("#B026FF"), // Electric Purple
    Color.parseColor("#FFE500"), // Laser Yellow
    Color.parseColor("#0088FF")  // Cyber Blue
  )

  private class StreamItem(
    var x: Float,
    var y: Float,
    var length: Float,
    var speed: Float,
    var strokeWidth: Float,
    var colorIdx: Int
  )

  private val streams = mutableListOf<StreamItem>()
  private var scanY = 0f
  private var lastFrameTime = System.currentTimeMillis()
  private var colorCycleTime = 0f
  private val bgRect = RectF()
  private val clipPath = Path()
  private val cornerRadius = 12f * context.resources.displayMetrics.density

  init {
    setWillNotDraw(false) // onDraw চালু রাখার জন্য
    orientation = VERTICAL
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    bgRect.set(2f, 2f, w.toFloat() - 2f, h.toFloat() - 2f)
    clipPath.reset()
    clipPath.addRoundRect(bgRect, cornerRadius, cornerRadius, Path.Direction.CW)

    streams.clear()
    if (w > 0 && h > 0) {
      val totalStreams = 11
      val stepX = w.toFloat() / (totalStreams + 1)
      for (i in 0 until totalStreams) {
        streams.add(
          StreamItem(
            x = (i + 1) * stepX,
            y = (Math.random() * (h + 30)).toFloat(),
            length = (20 + Math.random() * 30).toFloat(),
            speed = (90 + Math.random() * 120).toFloat(),
            strokeWidth = (1.6f + Math.random().toFloat() * 1.2f),
            colorIdx = (Math.random() * cyberColors.size).toInt()
          )
        )
      }
    }
  }

  override fun onDraw(canvas: Canvas) {
    val now = System.currentTimeMillis()
    val dt = ((now - lastFrameTime).coerceIn(1, 60)) / 1000f
    lastFrameTime = now

    colorCycleTime += dt * 0.9f
    val activeColor = cyberColors[(colorCycleTime.toInt()) % cyberColors.size]

    val w = width.toFloat()
    val h = height.toFloat()

    // ১. ব্যাকগ্রাউন্ড ড্র করা
    canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, bgPaint)

    // ২. হ্যাকিং লাইন যাতে ডায়ালগের গোল বর্ডারের বাইরে না যায় সেজন্য ক্লিপ করা
    canvas.save()
    canvas.clipPath(clipPath)

    // ৩. ডায়ালগের ভেতরের মুভিং স্ক্যানার লাইন
    scanY = (scanY + dt * 110f) % (h + 10f)
    linePaint.strokeWidth = 2f
    linePaint.color = activeColor
    linePaint.alpha = 180
    canvas.drawLine(0f, scanY, w, scanY, linePaint)

    // ৪. ডায়ালগের ভেতরের কালারফুল ডাটা স্ট্রিম লাইন
    for (stream in streams) {
      stream.y += stream.speed * dt
      if (stream.y - stream.length > h) {
        stream.y = -5f
        stream.colorIdx = (stream.colorIdx + 1) % cyberColors.size
      }

      val col = cyberColors[stream.colorIdx % cyberColors.size]
      linePaint.color = col
      linePaint.strokeWidth = stream.strokeWidth
      linePaint.alpha = 210

      val startY = (stream.y - stream.length).coerceAtLeast(0f)
      val endY = stream.y.coerceAtMost(h)
      if (endY > startY) {
        canvas.drawLine(stream.x, startY, stream.x, endY, linePaint)
        // উজ্জ্বল সাদা হেড
        linePaint.color = Color.WHITE
        linePaint.alpha = 240
        canvas.drawLine(stream.x, (endY - 5f).coerceAtLeast(0f), stream.x, endY, linePaint)
      }
    }
    canvas.restore()

    // ৫. নিয়ন বর্ডার ড্র করা
    canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, borderPaint)

    super.onDraw(canvas)
    postInvalidateOnAnimation()
  }
}

class OverlayService : Service() {

  companion object {
    const val CHANNEL_ID = "floating_overlay_channel"
    const val NOTIFICATION_ID = 101
    var isRunning = false
  }

  private var windowManager: WindowManager? = null
  private var floatingRootView: FrameLayout? = null
  private var floatingIconView: FrameLayout? = null
  private var dialogView: CyberHackerDialogLayout? = null
  private var layoutParams: WindowManager.LayoutParams? = null
  private var aimDotView: View? = null
  private var aimDotParams: WindowManager.LayoutParams? = null

  private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    isRunning = true
    createNotificationChannel()
    startForeground(NOTIFICATION_ID, createNotification())
    initOverlayView()
    initAimDot()
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
      .setContentTitle(".BABA PNAL")
      .setContentText("Floating button is active")
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
      y = 250
    }
    layoutParams = params

    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    floatingRootView = FrameLayout(this)

    // ১. ফ্লোটিং সার্কুলার আইকন (৪৮dp ছোট সাইজ)
    val iconSize = dp(48)
    floatingIconView = FrameLayout(this).apply {
      layoutParams = FrameLayout.LayoutParams(iconSize, iconSize)
      val bg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#080E0A"))
        setStroke(dp(2), Color.parseColor("#00FF66"))
      }
      background = bg
      elevation = dp(8).toFloat()

      val label = TextView(this@OverlayService).apply {
        text = ".BABA"
        setTextColor(Color.parseColor("#00FF66"))
        textSize = 11.5f
        typeface = Typeface.MONOSPACE
        paint.isFakeBoldText = true
        gravity = Gravity.CENTER
      }
      addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    // ২. ফ্লোটিং ডায়ালগ - একদম কমপ্যাক্ট ছোট সাইজ (চওড়া ২০০dp, উচ্চতা মাত্র ভেতরের কন্টেন্ট পরিমাণ ~১২০dp)
    dialogView = CyberHackerDialogLayout(this).apply {
      visibility = View.GONE
      elevation = dp(12).toFloat()
      val pad = dp(10)
      setPadding(pad, pad, pad, pad)
      layoutParams = FrameLayout.LayoutParams(dp(200), FrameLayout.LayoutParams.WRAP_CONTENT)

      // Top Row: Title + Close Button
      val topRow = LinearLayout(this@OverlayService).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val title = TextView(this@OverlayService).apply {
          text = "DOWNLOAD"
          setTextColor(Color.parseColor("#00FF66"))
          textSize = 13f
          typeface = Typeface.MONOSPACE
          paint.isFakeBoldText = true
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(this@OverlayService).apply {
          text = "✕"
          setTextColor(Color.parseColor("#00FF66"))
          textSize = 15f
          typeface = Typeface.MONOSPACE
          setPadding(dp(6), 0, dp(4), 0)
          setOnClickListener {
            showFloatingIcon()
          }
        }
        addView(title)
        addView(closeBtn)
      }
      addView(topRow)

      // Target path
      val targetHint = TextView(this@OverlayService).apply {
        text = "> com.arafat.com"
        setTextColor(Color.parseColor("#00F0FF"))
        textSize = 10f
        typeface = Typeface.MONOSPACE
        setPadding(0, dp(2), 0, dp(5))
      }
      addView(targetHint)

      // Status text
      val statusText = TextView(this@OverlayService).apply {
        text = "> Ready"
        setTextColor(Color.parseColor("#FFE500"))
        textSize = 10.5f
        typeface = Typeface.MONOSPACE
        setPadding(0, 0, 0, dp(5))
      }

      // Aim Dot Switch Row (Gaming Crosshair for FF/PUBG)
      val aimRow = LinearLayout(this@OverlayService).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = dp(8)
        val padV = dp(3)
        setPadding(padH, padV, padH, padV)
        val rowBg = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(6).toFloat()
          setColor(Color.parseColor("#D90C1610"))
          setStroke(dp(1), Color.parseColor("#FF1744"))
        }
        background = rowBg

        val aimLabel = TextView(this@OverlayService).apply {
          text = "Aim Dot"
          setTextColor(Color.WHITE)
          textSize = 12f
          typeface = Typeface.MONOSPACE
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val aimSwitch = Switch(this@OverlayService).apply {
          isChecked = true

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            thumbTintList = ColorStateList(
              arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
              intArrayOf(Color.parseColor("#FF1744"), Color.parseColor("#9CA3AF"))
            )
            trackTintList = ColorStateList(
              arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
              intArrayOf(Color.parseColor("#7F1D1D"), Color.parseColor("#374151"))
            )
          }

          setOnCheckedChangeListener { _, isChecked ->
            aimDotView?.visibility = if (isChecked) View.VISIBLE else View.GONE
          }
        }
        addView(aimLabel)
        addView(aimSwitch)
      }
      addView(aimRow)

      val aimSpacer = View(this@OverlayService).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4))
      }
      addView(aimSpacer)

      // Download Switch Row
      val switchRow = LinearLayout(this@OverlayService).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = dp(8)
        val padV = dp(3)
        setPadding(padH, padV, padH, padV)
        val rowBg = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(6).toFloat()
          setColor(Color.parseColor("#D90C1610"))
          setStroke(dp(1), Color.parseColor("#00FF66"))
        }
        background = rowBg

        val switchLabel = TextView(this@OverlayService).apply {
          text = "Download"
          setTextColor(Color.WHITE)
          textSize = 12f
          typeface = Typeface.MONOSPACE
          layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val downloadSwitch = Switch(this@OverlayService).apply {
          isChecked = false

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            thumbTintList = ColorStateList(
              arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
              intArrayOf(Color.parseColor("#00FF66"), Color.parseColor("#9CA3AF"))
            )
            trackTintList = ColorStateList(
              arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
              intArrayOf(Color.parseColor("#166534"), Color.parseColor("#374151"))
            )
          }

          setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
              statusText.text = "> Copying..."
              statusText.setTextColor(Color.parseColor("#FFE500"))
              serviceScope.launch {
                val result = withContext(Dispatchers.IO) {
                  ShizukuManager.copyFilesToTarget(applicationContext)
                }
                if (result.first) {
                  statusText.text = "> Completed"
                  statusText.setTextColor(Color.parseColor("#00FF66"))
                  Toast.makeText(applicationContext, result.second, Toast.LENGTH_SHORT).show()
                } else {
                  statusText.text = "> Failed"
                  statusText.setTextColor(Color.parseColor("#FF3355"))
                  Toast.makeText(applicationContext, result.second, Toast.LENGTH_SHORT).show()
                }
              }
            } else {
              statusText.text = "> Ready"
              statusText.setTextColor(Color.parseColor("#FFE500"))
            }
          }
        }
        addView(switchLabel)
        addView(downloadSwitch)
      }
      addView(switchRow)

      val delSpacer = View(this@OverlayService).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4))
      }
      addView(delSpacer)

      // Delete Files Button
      val deleteButton = TextView(this@OverlayService).apply {
        text = "Delete Files"
        setTextColor(Color.parseColor("#FF4444"))
        textSize = 11.5f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        val padH = dp(8)
        val padV = dp(4)
        setPadding(padH, padV, padH, padV)
        val btnBg = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(6).toFloat()
          setColor(Color.parseColor("#D9180808"))
          setStroke(dp(1), Color.parseColor("#FF4444"))
        }
        background = btnBg
        setOnClickListener {
          statusText.text = "> Deleting..."
          statusText.setTextColor(Color.parseColor("#FFE500"))
          serviceScope.launch {
            val result = withContext(Dispatchers.IO) {
              ShizukuManager.deleteFilesFromTarget(applicationContext)
            }
            if (result.first) {
              statusText.setTextColor(Color.parseColor("#00FF66"))
              Toast.makeText(applicationContext, "Files deleted. Closing in 5s...", Toast.LENGTH_SHORT).show()
              for (sec in 5 downTo 1) {
                statusText.text = "> Deleted. Closing in ${sec}s..."
                kotlinx.coroutines.delay(1000L)
              }
            } else {
              statusText.setTextColor(Color.parseColor("#FF3355"))
              Toast.makeText(applicationContext, "Delete failed. Closing in 5s...", Toast.LENGTH_SHORT).show()
              for (sec in 5 downTo 1) {
                statusText.text = "> Failed. Closing in ${sec}s..."
                kotlinx.coroutines.delay(1000L)
              }
            }
            stopSelf()
          }
        }
      }
      addView(deleteButton)

      val spacer = View(this@OverlayService).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6))
      }
      addView(spacer)

      addView(statusText)

      // Close Overlay Button
      val stopButton = TextView(this@OverlayService).apply {
        text = "[ Close Overlay ]"
        setTextColor(Color.parseColor("#FF4444"))
        textSize = 10.5f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(4), dp(6), dp(4))
        val btnBg = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(6).toFloat()
          setColor(Color.parseColor("#E6180808"))
          setStroke(dp(1), Color.parseColor("#FF3355"))
        }
        background = btnBg
        setOnClickListener {
          stopSelf()
        }
      }
      addView(stopButton)
    }

    floatingRootView?.addView(floatingIconView)
    floatingRootView?.addView(dialogView)

    // Drag and Click Listener
    var initialX = 0
    var initialY = 0
    var initialTouchX = 0f
    var initialTouchY = 0f

    val touchListener = View.OnTouchListener { _, event ->
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
            if (dialogView?.visibility == View.VISIBLE) {
              showFloatingIcon()
            } else {
              showDialogCard()
            }
          }
          true
        }
        else -> false
      }
    }

    floatingIconView?.setOnTouchListener(touchListener)

    try {
      windowManager?.addView(floatingRootView, layoutParams)
    } catch (e: Exception) {
      Toast.makeText(this, "Overlay Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
  }

  private fun showDialogCard() {
    floatingIconView?.visibility = View.GONE
    dialogView?.visibility = View.VISIBLE
    // উইন্ডো ম্যানেজার সাইজ আপডেট
    try {
      layoutParams?.width = WindowManager.LayoutParams.WRAP_CONTENT
      layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
      windowManager?.updateViewLayout(floatingRootView, layoutParams)
    } catch (_: Exception) {}
  }

  private fun showFloatingIcon() {
    dialogView?.visibility = View.GONE
    floatingIconView?.visibility = View.VISIBLE
    try {
      layoutParams?.width = WindowManager.LayoutParams.WRAP_CONTENT
      layoutParams?.height = WindowManager.LayoutParams.WRAP_CONTENT
      windowManager?.updateViewLayout(floatingRootView, layoutParams)
    } catch (_: Exception) {}
  }

  private fun initAimDot() {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    // Gaming Crosshair Dot for Free Fire / PUBG (8dp red dot with 1dp black border)
    val dotSize = dp(8)
    val dot = View(this).apply {
      val bg = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor("#FF1744"))
        setStroke(dp(1), Color.parseColor("#000000"))
      }
      background = bg
    }

    val params = WindowManager.LayoutParams(
      dotSize,
      dotSize,
      layoutFlag,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.CENTER
      x = 0
      y = 0
    }

    aimDotView = dot
    aimDotParams = params

    try {
      windowManager?.addView(aimDotView, aimDotParams)
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    super.onDestroy()
    isRunning = false
    try {
      floatingRootView?.let { windowManager?.removeView(it) }
    } catch (_: Exception) {}
    try {
      aimDotView?.let { windowManager?.removeView(it) }
    } catch (_: Exception) {}
  }
}
