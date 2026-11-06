package de.jakob.picopix

import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.hardware.usb.*
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var usb: UsbManager
    private lateinit var status: TextView
    private lateinit var log: TextView
    private val permissionAction = "de.jakob.picopix.USB_PERMISSION"

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i?.action == permissionAction) {
                val d = i.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                append("USB-Berechtigung: ${if (i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) "erteilt" else "abgelehnt"} ${describe(d)}")
            }
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b); setContentView(R.layout.activity_main)
        usb = getSystemService(USB_SERVICE) as UsbManager
        status = findViewById(R.id.status); log = findViewById(R.id.log)
        registerReceiver(receiver, IntentFilter(permissionAction), RECEIVER_NOT_EXPORTED)
        findViewById<Button>(R.id.scan).setOnClickListener { scan() }
        findViewById<Button>(R.id.switchMode).setOnClickListener { modeSwitch() }
        findViewById<Button>(R.id.capture).setOnClickListener {
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), 2055)
        }
        scan()
    }

    private fun scan(): UsbDevice? {
        val devices = usb.deviceList.values.toList()
        log.text = ""
        devices.forEach { d ->
            append(describe(d))
            for (n in 0 until d.interfaceCount) {
                val f=d.getInterface(n); append("  IF $n class=${f.interfaceClass} endpoints=${f.endpointCount}")
                for (e in 0 until f.endpointCount) { val ep=f.getEndpoint(e); append("    EP 0x${ep.address.toString(16)} type=${ep.type} dir=${ep.direction} max=${ep.maxPacketSize}") }
            }
        }
        val target = devices.firstOrNull { it.vendorId == 0x1de1 }
        status.text = if (target == null) "Kein AM7xxx-Geraet (VID 0x1de1) gefunden." else "AM7xxx gefunden: ${describe(target)}"
        if (target != null && !usb.hasPermission(target)) request(target)
        return target
    }

    private fun request(d: UsbDevice) {
        val p = PendingIntent.getBroadcast(this, 0, Intent(permissionAction), PendingIntent.FLAG_IMMUTABLE)
        usb.requestPermission(d, p)
    }

    private fun modeSwitch() {
        val d=scan() ?: return
        if (!usb.hasPermission(d)) { request(d); return }
        if (d.productId != 0x1101) { append("Kein Massenspeichermodus (PID ist nicht 0x1101). Umschalten uebersprungen."); return }
        val intf=(0 until d.interfaceCount).map { d.getInterface(it) }.firstOrNull { f -> (0 until f.endpointCount).any { f.getEndpoint(it).direction==UsbConstants.USB_DIR_OUT && f.getEndpoint(it).type==UsbConstants.USB_ENDPOINT_XFER_BULK } }
        if (intf == null) { append("Kein Bulk-OUT-Endpunkt gefunden."); return }
        val ep=(0 until intf.endpointCount).map { intf.getEndpoint(it) }.first { it.direction==UsbConstants.USB_DIR_OUT && it.type==UsbConstants.USB_ENDPOINT_XFER_BULK }
        val c=usb.openDevice(d) ?: run { append("openDevice fehlgeschlagen"); return }
        try {
            if (!c.claimInterface(intf, true)) { append("claimInterface fehlgeschlagen"); return }
            // USB_ModeSwitch CBW from libam7xxx documentation.
            val cbw=hex("55534243087052890000000000000cff020000000000000000000000000000")
            val sent=c.bulkTransfer(ep, cbw, cbw.size, 3000)
            append("Mode-switch CBW: $sent/${cbw.size} Byte. Beamer sollte sich neu anmelden; 3-5 s warten und erneut pruefen.")
        } finally { try { c.releaseInterface(intf) } catch (_:Exception){}; c.close() }
    }

    override fun onActivityResult(r:Int, code:Int, data:Intent?) {
        super.onActivityResult(r,code,data)
        if(r==2055) append(if(code==RESULT_OK) "MediaProjection-Freigabe erfolgreich. Naechster Schritt: Frames 800x480, JPEG Q55, max. 15 fps ueber AM7xxx senden." else "Bildschirmfreigabe abgebrochen.")
    }

    private fun describe(d:UsbDevice?):String = if(d==null) "(kein Geraet)" else String.format(Locale.US,"VID=%04x PID=%04x name=%s",d.vendorId,d.productId,d.deviceName)
    private fun append(s:String) { log.append(s+"\n") }
    private fun hex(s:String)=ByteArray(s.length/2){ s.substring(it*2,it*2+2).toInt(16).toByte() }
    override fun onDestroy(){ unregisterReceiver(receiver); super.onDestroy() }
}
