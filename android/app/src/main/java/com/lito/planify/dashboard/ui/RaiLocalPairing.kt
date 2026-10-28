package com.lito.planify.dashboard.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.lito.planify.dashboard.data.Store
import com.lito.planify.dashboard.data.PersonalCalendar
import java.util.UUID
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Vinculació local temporal. Només s'obre mentre la pantalla de QR és visible.
 * El QR no incorpora cap secret familiar; la transferència HTTP es fa dins la Wi-Fi privada.
 */
internal class RaiLocalPairing(private val context:Context,private val onPaired:(String,String)->Unit):AutoCloseable {
    private val random=ByteArray(20).also { SecureRandom().nextBytes(it) }
    private val token=random.joinToString("") { "%02x".format(it) }
    private val live=AtomicBoolean(true)
    private val expiry=System.currentTimeMillis()+5*60_000L
    private val server=ServerSocket(0).apply { soTimeout=1000 }
    private val worker=Thread {
        while(live.get()&&System.currentTimeMillis()<expiry){
            try {
                val client = server.accept()
                Thread {
                    client.use { socket ->
                        try {
                            handle(socket)
                        } catch (_: Exception) {
                            // Si falla la lectura, el navegador no s'ha de quedar esperant.
                            runCatching {
                                respond(socket, 500,
                                    "<html lang='ca'><meta name='viewport' content='width=device-width'><body><h2>No s'ha pogut connectar</h2><p>Torna al marc, genera un QR nou i repeteix la vinculació.</p></body></html>")
                            }
                        }
                    }
                }.apply { isDaemon = true; start() }
            } catch (_: java.net.SocketTimeoutException) {
                // Comprova periòdicament si el codi ha caducat.
            } catch (_: Exception) {
                if (!live.get()) break
            }
        }
        close()
    }.apply { isDaemon=true;start() }

    val link:String? get() {
        val wifi=context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val ip=wifi.connectionInfo?.ipAddress ?: 0
        if(ip==0)return null
        val host=(0..3).joinToString(".") { ((ip shr (it*8)) and 255).toString() }
        return "http://$host:${server.localPort}/$token"
    }

    private fun respond(socket:Socket,code:Int,message:String,type:String="text/html; charset=utf-8") {
        val body=message.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().write(
            ("HTTP/1.1 $code "+(if(code==200)"OK" else "Error")+"\r\n"+
            "Content-Type: $type\r\nCache-Control: no-store\r\n"+
            "X-Content-Type-Options: nosniff\r\n"+
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; form-action 'self'\r\n"+
            "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII)
        )
        socket.getOutputStream().write(body)
        socket.getOutputStream().flush()
    }
    private fun handle(socket:Socket) {
        socket.soTimeout=6000
        if(System.currentTimeMillis()>expiry){respond(socket,410,"Caducat");return}
        val stream=socket.getInputStream()
        val reader=java.io.BufferedReader(java.io.InputStreamReader(stream,Charsets.UTF_8))
        val request=reader.readLine() ?: return
        val parts=request.split(" ")
        if(parts.size<2 || parts[1]!="/$token"){respond(socket,404,"No disponible");return}
        var length=0
        while(true) {
            val header=reader.readLine() ?: return
            if(header.isEmpty())break
            if(header.startsWith("Content-Length:",true))length=header.substringAfter(":").trim().toIntOrNull() ?: -1
        }
        if(parts[0]=="GET") {
            respond(socket,200,"""<!doctype html><html lang="ca"><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Connectar Marc Familiar</title><style>body{font-family:system-ui;max-width:480px;margin:35px auto;padding:18px}input,button{box-sizing:border-box;width:100%;padding:14px;margin:10px 0;border-radius:10px;border:1px solid #bbb}button{background:#222;color:white}</style></head><body><h2>Connectar Marc Familiar</h2><p>Introdueix les dades des del mòbil. El codi caduca al cap de cinc minuts.</p><form method="post"><label>URL d'Apps Script (/exec)<input name="url" type="url" required></label><label>Clau familiar de compra i tasques<input name="key" type="password" required minlength="64" maxlength="64"></label><hr><h3>Fotos compartides (opcional)</h3><label>Clau privada de lectura de fotos<input name="photoKey" type="password" minlength="64" maxlength="64"></label><hr><h3>Calendaris Google (opcional)</h3><p>Enganxa les adreces privades iCal, una per línia, fins a 5. Només Google Calendar publicat com a enllaç secret iCal, no la URL normal del navegador.</p><textarea name="calendars" rows="4" style="box-sizing:border-box;width:100%;padding:12px" placeholder="Nom del calendari | https://calendar.google.com/calendar/ical/..."></textarea><button>Connectar el marc</button></form><p>Fes-ho només a la Wi-Fi privada de casa.</p></body></html>""")
            return
        }
        if(parts[0]!="POST" || length<1 || length>4000){respond(socket,400,"Petició invàlida");return}
        // BufferedReader.read(char[]) conserva el contingut ASCII dels camps percent-encoded.
        val content=CharArray(length)
        var received=0
        while(received<length){
            val n=reader.read(content,received,length-received)
            if(n<1)return
            received+=n
        }
        val fields=String(content).split("&").mapNotNull {
            val pair=it.split("=",limit=2)
            if(pair.size!=2)null else URLDecoder.decode(pair[0],"UTF-8") to URLDecoder.decode(pair[1],"UTF-8")
        }.toMap()
        val url=fields["url"].orEmpty().trim()
        val key=fields["key"].orEmpty().trim()
        val photoKey=fields["photoKey"].orEmpty().trim()
        val calendarLines=fields["calendars"].orEmpty().lines().map { it.trim() }.filter { it.isNotBlank() }
        val calendars=calendarLines.mapNotNull { line ->
            val bits=line.split("|",limit=2)
            if(bits.size!=2)return@mapNotNull null
            val label=bits[0].trim().take(60)
            val ical=bits[1].trim()
            val u=ical.toHttpUrlOrNull()
            if(label.isBlank()||u==null||!u.isHttps||!(u.host=="calendar.google.com"||u.host=="www.google.com")||!u.encodedPath.contains("/ical/"))null
            else PersonalCalendar(UUID.randomUUID().toString(),label,ical)
        }
        val good=runCatching {
            val u=url.toHttpUrlOrNull()
            u!=null&&u.isHttps&&u.host=="script.google.com"&&u.encodedPath.endsWith("/exec")&&
                key.matches(Regex("[a-fA-F0-9]{64}")) &&
                (photoKey.isBlank()||photoKey.matches(Regex("[a-fA-F0-9]{64}"))) &&
                calendarLines.size<=5 && calendars.size==calendarLines.size
        }.getOrDefault(false)
        if(!good){respond(socket,400,"Dades incorrectes: revisa l'URL /exec i la clau de 64 caràcters");return}
        context.getSharedPreferences("marc_rai_sync",Context.MODE_PRIVATE).edit()
            .putString("url",url).putString("key",key).apply()
        if(photoKey.isNotBlank()) {
            context.getSharedPreferences("marc_fotos",Context.MODE_PRIVATE).edit()
                .putString("shared_url",url).putString("shared_key",photoKey).apply()
        }
        if(calendars.isNotEmpty())Store(context).calendarFeeds=calendars
        respond(socket,200,"<html lang='ca'><meta name='viewport' content='width=device-width'><body><h2>Marc vinculat!</h2><p>Ja pots tornar a la pantalla del marc. Per activar els nous calendaris, reinicia l’aplicació.</p></body></html>")
        android.os.Handler(android.os.Looper.getMainLooper()).post { onPaired(url,key) }
        close()
    }
    override fun close() { if(live.getAndSet(false)) runCatching {server.close()} }
}
internal fun raiPairingQr(data:String):Bitmap {
    val bits:BitMatrix=MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,380,380)
    return Bitmap.createBitmap(380,380,Bitmap.Config.ARGB_8888).also { bitmap ->
        for(y in 0 until 380)for(x in 0 until 380)
            bitmap.setPixel(x,y,if(bits[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    }
}
@Composable
internal fun RaiPairingView(onPaired:(String,String)->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    var address by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        val service=RaiLocalPairing(context,onPaired)
        address=service.link
        onDispose {service.close()}
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Escaneja el QR amb el mòbil (mateixa Wi-Fi). Caduca en 5 minuts.")
        if(address!=null){
            val bitmap=remember(address){raiPairingQr(address!!)}
            Image(bitmap.asImageBitmap(),contentDescription="QR temporal per vincular el marc",modifier=Modifier.size(380.dp))
            OutlinedButton(onClick={
                context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(address!!)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }) { Text("Configurar des d’aquest mateix mòbil") }
        }else Text("Connecta el marc a la Wi-Fi per mostrar el QR.")
        Text("El QR no conté contrasenyes. Fes-lo servir només a la teva Wi-Fi privada.")
    }
}
