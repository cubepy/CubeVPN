package net.cubevpn.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.widget.RemoteViews
import androidx.core.content.ContextCompat

/**
 * Home-screen connect/disconnect toggle, built on the plain
 * AppWidgetProvider/RemoteViews framework APIs only — no androidx.glance,
 * no WorkManager. A prior Glance-based widget transitively pulled in
 * WorkManager, whose auto-init crashed the whole app at process start under
 * R8 in release builds (see git history: "Revert the home-screen widget").
 * These classic APIs need zero extra dependencies and use the same
 * connect/disconnect logic already proven safe in QsTileService.
 */
class CubeVpnWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        VpnBridge.register(context.applicationContext)
        appWidgetIds.forEach { updateWidget(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            VpnBridge.register(context.applicationContext)
            toggle(context)
            refreshAll(context)
        }
    }

    private fun toggle(context: Context) {
        val store = ConfigStore.get(context.applicationContext)
        val state = VpnState.state.value
        if (state == Connection.CONNECTED || state == Connection.CONNECTING) {
            runCatching {
                context.startService(
                    Intent(context, CubeVpnService::class.java).setAction(CubeVpnService.ACTION_STOP)
                )
            }
            VpnState.setDisconnected()
            return
        }
        val selectedId = store.selectedId.value
        val config = store.configs.value.firstOrNull { it.id == selectedId }
        if (config == null || VpnService.prepare(context) != null) {
            openApp(context)
            return
        }
        val json = ConfigBuilder.build(
            config, store.fragment.value, store.splitRouting.value,
            store.sniffing.value, store.sniffTypes.value
        )
        VpnState.setConnecting(config.id)
        val svcIntent = Intent(context, CubeVpnService::class.java)
            .putExtra(CubeVpnService.EXTRA_CONFIG, json)
            .putExtra(CubeVpnService.EXTRA_NAME, config.name)
            .putExtra(CubeVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
        runCatching { ContextCompat.startForegroundService(context, svcIntent) }
            .onFailure { VpnState.setDisconnected(); openApp(context) }
    }

    private fun openApp(context: Context) {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        const val ACTION_TOGGLE = "net.cubevpn.app.WIDGET_TOGGLE"

        /** Called from VpnBridge whenever the VPN state settles, so every widget instance stays live. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CubeVpnWidget::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            val store = runCatching { ConfigStore.get(context.applicationContext) }.getOrNull()
            val lang = store?.lang?.value ?: Lang.EN
            val state = VpnState.state.value
            val selectedName = store?.let { st ->
                val cid = st.selectedId.value
                st.configs.value.firstOrNull { it.id == cid }?.name
            }
            val statusText = when (state) {
                Connection.CONNECTED -> Strings.get(lang, "status_connected")
                Connection.CONNECTING -> Strings.get(lang, "status_connecting")
                Connection.ERROR -> Strings.get(lang, "status_error")
                Connection.DISCONNECTED -> selectedName ?: Strings.get(lang, "tap_choose")
            }
            val dot = when (state) {
                Connection.CONNECTED -> R.drawable.widget_dot_connected
                Connection.CONNECTING -> R.drawable.widget_dot_connecting
                Connection.ERROR -> R.drawable.widget_dot_error
                Connection.DISCONNECTED -> R.drawable.widget_dot_off
            }
            val views = RemoteViews(context.packageName, R.layout.widget_cubevpn).apply {
                setTextViewText(R.id.widget_title, Strings.get(lang, "app_title"))
                setTextViewText(R.id.widget_status, statusText)
                setImageViewResource(R.id.widget_toggle_dot, dot)
                val toggleIntent = Intent(context, CubeVpnWidget::class.java).setAction(ACTION_TOGGLE)
                val pi = PendingIntent.getBroadcast(
                    context, id, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.widget_root, pi)
            }
            runCatching { manager.updateAppWidget(id, views) }
        }
    }
}
