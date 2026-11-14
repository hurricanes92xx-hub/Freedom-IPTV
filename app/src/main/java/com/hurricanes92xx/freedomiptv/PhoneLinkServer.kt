package com.hurricanes92.freedomiptv
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import fi.iki.elonen.NanoHTTPD
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Temporary local-network phone pairing. PIN + random URL token; one successful submission. */
class PhoneLinkServer(private val onCredentials: (String, String, String) -> Unit) : NanoHTTPD(8765) {
    private val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    val pin = "%06d".format(SecureRandom().nextInt(1_000_000))
    private val used = AtomicBoolean(false)
    private val expires = System.currentTimeMillis() + 10 * 60_000L
    val url: String? = localIp()?.let { "http://$it:8765/?token=$token" }
    fun qr(size: Int = 420): Bitmap? = url?.let { try {
        val matrix = QRCodeWriter().encode(it, BarcodeFormat.QR_CODE, size, size)
        Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bmp -> for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (matrix[x,y]) Color.BLACK else Color.WHITE) }
    } catch (_: Exception) { null } }
    override fun serve(session: IHTTPSession): Response {
        if (System.currentTimeMillis() > expires || used.get()) return newFixedLengthResponse(Response.Status.GONE, MIME_PLAINTEXT, "Pairing expired. Start again on TV.")
        if (session.parameters["token"]?.firstOrNull() != token) return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Invalid link")
        if (session.method == Method.GET) return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html())
        if (session.method != Method.POST) return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "POST only")
        return try {
            val files = HashMap<String, String>(); session.parseBody(files); val p = session.parameters
            if (p["pin"]?.firstOrNull() != pin) return newFixedLengthResponse(Response.Status.FORBIDDEN, "text/html", html("TV code did not match."))
            val server = p["server"]?.firstOrNull()?.trim().orEmpty(); val user = p["username"]?.firstOrNull()?.trim().orEmpty(); val pass = p["password"]?.firstOrNull().orEmpty()
            if (!server.startsWith("https://", true) || user.isBlank() || pass.isBlank()) return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", html("Enter HTTPS server, username and password."))
            if (!used.compareAndSet(false, true)) return newFixedLengthResponse(Response.Status.GONE, MIME_PLAINTEXT, "Already paired")
            onCredentials(server, user, pass)
            newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", "<meta name=viewport content=width=device-width><body style=font:20px system-ui;text-align:center;padding:30px><h2>Connected to Freedom IPTV</h2>Return to your TV.</body>")
        } catch (_: Exception) { newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Could not read form") }
    }
    private fun html(message: String = "") = """<!doctype html><html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Freedom Link</title><body style="font:16px system-ui;max-width:480px;margin:24px auto;padding:0 18px;background:#080b10;color:white">
      <h1 style="color:#f04452">Freedom Link</h1><p>Enter the six-digit code shown on your TV and your Xtream credentials.</p><p style="color:#ff8a92">$message</p>
      <form method="post" action="/?token=$token"><label>TV pairing code</label><input name="pin" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" required style="width:100%;padding:12px;box-sizing:border-box;margin:8px 0 16px">
      <label>Xtream HTTPS server</label><input name="server" type="url" placeholder="https://provider.example:port" required style="width:100%;padding:12px;box-sizing:border-box;margin:8px 0 16px">
      <label>Username</label><input name="username" autocomplete="username" required style="width:100%;padding:12px;box-sizing:border-box;margin:8px 0 16px">
      <label>Password</label><input name="password" type="password" autocomplete="current-password" required style="width:100%;padding:12px;box-sizing:border-box;margin:8px 0 20px">
      <button style="width:100%;padding:14px;background:#f04452;color:white;border:0;border-radius:8px;font-size:17px;font-weight:bold">Connect to TV</button></form><p style="color:#9aa6b8;font-size:13px">Same trusted Wi-Fi only. Link expires in 10 minutes and can be used once.</p></body></html>"""
    private fun localIp(): String? = try { NetworkInterface.getNetworkInterfaces().toList().asSequence().flatMap { it.inetAddresses.toList().asSequence() }.filter { !it.isLoopbackAddress && it.hostAddress?.contains(":") == false }.mapNotNull { it.hostAddress }.firstOrNull() } catch (_: Exception) { null }
}