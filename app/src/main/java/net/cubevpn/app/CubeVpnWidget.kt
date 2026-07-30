package net.cubevpn.app

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/** Medium (~4x2) home-screen widget: connection status + selected server, tap anywhere to toggle. */
class CubeVpnWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        VpnBridge.register(context)
        val store = ConfigStore.get(context)
        store.awaitReady()
        val lang = store.lang.value
        val conn = VpnState.state.value
        val selectedName = store.configs.value.firstOrNull { it.id == store.selectedId.value }?.name
        val statusText = when (conn) {
            Connection.CONNECTED -> Strings.get(lang, "status_connected")
            Connection.CONNECTING -> Strings.get(lang, "status_connecting")
            Connection.ERROR -> Strings.get(lang, "status_error")
            Connection.DISCONNECTED -> Strings.get(lang, "status_disconnected")
        }
        val subtitle = selectedName ?: Strings.get(lang, "tap_choose")

        provideContent {
            WidgetContent(statusText = statusText, subtitle = subtitle, connected = conn == Connection.CONNECTED)
        }
    }
}

@Composable
private fun WidgetContent(statusText: String, subtitle: String, connected: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0D0619)))
            .padding(16.dp)
            .clickable(actionRunCallback<ToggleConnectionAction>()),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            "CubeVPN",
            style = TextStyle(color = ColorProvider(Color(0xFFA855F7)), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            statusText,
            style = TextStyle(
                color = ColorProvider(if (connected) Color(0xFFC026D3) else Color.White),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            subtitle,
            style = TextStyle(color = ColorProvider(Color(0xFFC3B3D6)), fontSize = 13.sp)
        )
    }
}

class CubeVpnWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CubeVpnWidget()
}

class ToggleConnectionAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        VpnBridge.register(context)
        val store = ConfigStore.get(context)
        store.awaitReady()
        val conn = VpnState.state.value

        if (conn == Connection.CONNECTED || conn == Connection.CONNECTING) {
            runCatching {
                context.startService(Intent(context, CubeVpnService::class.java).setAction(CubeVpnService.ACTION_STOP))
            }
            VpnState.setDisconnected()
        } else {
            val config = store.configs.value.firstOrNull { it.id == store.selectedId.value }
            if (config == null || VpnService.prepare(context) != null) {
                openApp(context)
            } else {
                val json = ConfigBuilder.build(
                    config, store.fragment.value, store.splitRouting.value,
                    store.sniffing.value, store.sniffTypes.value
                )
                VpnState.setConnecting(config.id)
                val intent = Intent(context, CubeVpnService::class.java)
                    .putExtra(CubeVpnService.EXTRA_CONFIG, json)
                    .putExtra(CubeVpnService.EXTRA_NAME, config.name)
                    .putExtra(CubeVpnService.EXTRA_STOP_LABEL, Strings.get(store.lang.value, "disconnect"))
                runCatching { ContextCompat.startForegroundService(context, intent) }
                    .onFailure { VpnState.setDisconnected(); openApp(context) }
            }
        }
        runCatching { CubeVpnWidget().updateAll(context) }
    }

    private fun openApp(context: Context) {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
