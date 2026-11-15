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

    /** 순정 내비 위에 뜨는 작은 팝업 */
    fun show(title: String, body: String) {
        try { nm?.notify(ID, builder(title, body).build()) } catch (_: Throwable) {}
    }

    /** 도착: 우리 앱을 앞으로 */
    fun bringToFront(title: String, body: String) {
        try { ctx.startActivity(Intent(ctx, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) } catch (_: Throwable) {}
        try { nm?.notify(ID, builder(title, body).setFullScreenIntent(open(), true).build()) } catch (_: Throwable) {}
    }

    companion object { const val CH = "h2_drive"; const val ID = 7 }
}
