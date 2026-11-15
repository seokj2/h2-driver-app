package kr.bighero.h2.driver.vehicle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 순정 내비로 길안내하는 동안 우리 앱이 띄우는 작은 팝업(헤즈업 알림)과, 도착하면 우리 앱을 다시 앞으로 부르기.
 *
 * - 운전 중에는 순정 내비가 화면을 쓰고, 우리 앱은 꼭 필요한 것(다음 목적지·도착 시각, 접안 지연·마감 위험 같은 알림)만 팝업으로.
 * - 도착하면 [bringToFront]로 우리 앱(터미널 컨테이너 위치·충전 화면)을 띄운다.
 *   안드로이드 10부터 백그라운드 앱의 화면 띄우기가 막혀 있어, 바로 띄우기에 실패하면 전체 화면 알림(fullScreenIntent)으로 연다.
 *   플레오스 정식 연동에서는 NaviHelperEventListener.onDestinationArrived()가 도착 신호가 된다(Navi.kt 주석).
 */
class Popups(private val ctx: Context, private val app: Class<*>) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)
    init {
        try { nm?.createNotificationChannel(NotificationChannel(CH, "운전 중 안내", NotificationManager.IMPORTANCE_HIGH)) } catch (_: Throwable) {}
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(ctx, 0,
        Intent(ctx, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun builder(title: String, body: String) = Notification.Builder(ctx, CH)
        .setSmallIcon(ctx.applicationInfo.icon).setContentTitle(title).setContentText(body)
        .setCategory(Notification.CATEGORY_NAVIGATION).setAutoCancel(true).setContentIntent(open())

    // ---- 떠 있는 팝업 창(다른 앱 위에 그리기 권한이 있을 때): 순정 내비 오른쪽 위에 우리 카드 ----
    private val wm = ctx.getSystemService(android.view.WindowManager::class.java)
    private var card: android.widget.LinearLayout? = null
    private var tTitle: android.widget.TextView? = null
    private var tBody: android.widget.TextView? = null
    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun overlay(title: String, body: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(ctx)) return false
        try {
            if (card == null) {
                val c = android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(dp(22), dp(18), dp(22), dp(20))
                    background = android.graphics.drawable.GradientDrawable().apply { setColor(0xFFFFFFFF.toInt()); cornerRadius = dp(18).toFloat(); setStroke(dp(1), 0xFFE2E5E9.toInt()) }
                    elevation = dp(12).toFloat()
                    setOnClickListener { ctx.startActivity(Intent(ctx, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
                }
                val head = android.widget.TextView(ctx).apply {
                    text = "  H2 회랑 기사"; textSize = 15f; setTextColor(0xFF16A37F.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                tTitle = android.widget.TextView(ctx).apply { textSize = 26f; setTextColor(0xFF111111.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) }
                tBody = android.widget.TextView(ctx).apply { textSize = 19f; setTextColor(0xFF555B63.toInt()) }
                c.addView(head); c.addView(tTitle); c.addView(tBody)
                val lp = android.view.WindowManager.LayoutParams(dp(560), android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                    android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, android.graphics.PixelFormat.TRANSLUCENT).apply {
                    gravity = android.view.Gravity.TOP or android.view.Gravity.END; x = dp(130); y = dp(90)
                }
                wm?.addView(c, lp); card = c
            }
            tTitle?.text = title; tBody?.text = body
            return true
        } catch (_: Throwable) { card = null; return false }
    }
    /** 팝업 창 닫기 */
    fun hide() { try { card?.let { wm?.removeView(it) } } catch (_: Throwable) {}; card = null }

    /** 순정 내비 위에 뜨는 팝업(창을 못 띄우면 헤즈업 알림) */
    fun show(title: String, body: String) {
        if (overlay(title, body)) return
        try { nm?.notify(ID, builder(title, body).build()) } catch (_: Throwable) {}
    }

    /** 도착: 팝업을 닫고 우리 앱을 앞으로 */
    fun bringToFront(title: String, body: String) {
        hide()
        try { ctx.startActivity(Intent(ctx, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (_: Throwable) {}
        try { nm?.notify(ID, builder(title, body).setFullScreenIntent(open(), true).build()) } catch (_: Throwable) {}
    }

    companion object { const val CH = "h2_drive"; const val ID = 7 }
}
