package com.example.shimejipet

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

class PetService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        const val PREFS = "pet_settings"
        private const val CHANNEL = "habibty_channel"
        private const val NOTIFICATION_ID = 77
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var loop: Runnable? = null
    private var petRoot: FrameLayout? = null
    private var image: ImageView? = null
    private var effectsRoot: FrameLayout? = null
    private var speech: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var effectParams: WindowManager.LayoutParams? = null

    private var side = 150
    private var speed = 100
    private var lift = 0
    private var x = 0f
    private var y = 0f
    private var vx = 1.5f
    private var facing = 1f
    private var enabled = true
    private var stayUntil = 0L
    private var speechUntil = 0L
    private var lastSpeech = 0L
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var ground = 0f
    private var stateUntil = 0L
    private var walking = false
    private var messages = listOf("Hello, my love ♡","I missed you!","You're doing great!")

    override fun onCreate() {
        super.onCreate()
        prefs=getSharedPreferences(PREFS,MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startAsForeground()
        readSettings()
        if (!Settings.canDrawOverlays(this) || !PetImage.exists(this)) { stopSelf(); return }
        try { createWindows() } catch (_:Exception) { stopSelf(); return }
        refreshMetrics()
        x=(resources.displayMetrics.widthPixels/resources.displayMetrics.density-side)/2f
        y=(ground-side).coerceAtLeast(0f)
        stateUntil=System.currentTimeMillis()+1500
        startLoop()
    }

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="com.example.shimejipet.STOP"){ stopSelf(); return START_NOT_STICKY }
        return START_STICKY
    }

    private fun createChannel() {
        if(Build.VERSION.SDK_INT>=26) {
            val c=NotificationChannel(CHANNEL,"Habibty",NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
        }
    }

    private fun startAsForeground() {
        val stopIntent=Intent(this,PetService::class.java).setAction("com.example.shimejipet.STOP")
        val stop=PendingIntent.getService(this,1,stopIntent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open=PendingIntent.getActivity(this,2,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n=Notification.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle("Habibty is here")
            .setContentText("Your little companion is with you.")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
        if(Build.VERSION.SDK_INT>=34) startForeground(NOTIFICATION_ID,n,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID,n)
    }

    private fun readSettings() {
        side=prefs.getInt("size",150).coerceIn(50,260)
        speed=prefs.getInt("speed",100).coerceIn(50,200)
        lift=prefs.getInt("lift",0)
        enabled=prefs.getBoolean("movement_enabled",true)
        messages=(prefs.getString("messages","Hello, my love ♡|I missed you!|You're doing great!") ?: "")
            .split("|").map{it.trim()}.filter{it.isNotEmpty()}.ifEmpty{listOf("Hello ♡")}
    }

    private fun createWindows() {
        val d=resources.displayMetrics.density
        effectsRoot=FrameLayout(this)
        effectParams=WindowManager.LayoutParams(-1,-1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START}
        wm.addView(effectsRoot,effectParams)

        petRoot=FrameLayout(this)
        image=ImageView(this).apply {
            setImageBitmap(BitmapFactory.decodeFile(PetImage.path(this@PetService)))
            scaleType=ImageView.ScaleType.FIT_CENTER
        }
        petRoot?.addView(image,FrameLayout.LayoutParams(-1,-1))
        params=WindowManager.LayoutParams((side*d).toInt(),(side*d).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP or Gravity.START}
        petRoot?.setOnTouchListener{_,e->handleTouch(e)}
        wm.addView(petRoot,params)
    }

    private fun refreshMetrics() {
        val m=resources.displayMetrics
        val h=m.heightPixels/m.density
        val w=m.widthPixels/m.density
        val id=resources.getIdentifier("navigation_bar_height","dimen","android")
        val nav=if(id!=0) resources.getDimension(id)/m.density else 0f
        ground=h-nav-lift
        x=x.coerceIn(0f,(w-side).coerceAtLeast(0f))
        y=y.coerceIn(0f,(ground-side).coerceAtLeast(0f))
    }

    private fun startLoop() {
        loop=object:Runnable {
            override fun run() {
                if(petRoot==null) return
                tick()
                handler.postDelayed(this,30)
            }
        }
        handler.post(loop!!)
    }

    private fun tick() {
        val now=System.currentTimeMillis()
        val width=resources.displayMetrics.widthPixels/resources.displayMetrics.density
        if(!dragging && enabled && now>=stayUntil) {
            if(now>=stateUntil) {
                walking=Random.nextInt(100)<55
                if(walking) { facing=if(Random.nextBoolean()) 1f else -1f; vx=1.2f*facing }
                stateUntil=now+Random.nextLong(1800,4500)
            }
            if(walking) {
                x+=vx*(speed/100f)
                if(x<0f || x>width-side) {
                    x=x.coerceIn(0f,(width-side).coerceAtLeast(0f))
                    facing*=-1f
                    vx=abs(vx)*facing
                }
                image?.scaleX=facing
            }
        } else {
            walking=false
        }
        if(now>=speechUntil) speech?.visibility=View.GONE
        if(now-lastSpeech>Random.nextLong(9000,15000) && messages.isNotEmpty()) {
            showSpeech(messages.random())
            lastSpeech=now
        }
        updatePosition()
    }

    private fun updatePosition() {
        val d=resources.displayMetrics.density
        params?.x=(x*d).toInt()
        params?.y=(y*d).toInt()
        try { if(petRoot?.isAttachedToWindow==true) wm.updateViewLayout(petRoot,params) } catch(_:Exception){}
    }

    private fun handleTouch(e:MotionEvent):Boolean {
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX=e.rawX; downY=e.rawY; dragging=false; return true }
            MotionEvent.ACTION_MOVE -> {
                val dx=e.rawX-downX; val dy=e.rawY-downY
                if(abs(dx)>8 || abs(dy)>8) dragging=true
                if(dragging) {
                    val d=resources.displayMetrics.density
                    x+=dx/d; y+=dy/d
                    downX=e.rawX; downY=e.rawY
                    updatePosition()
                }
                return true
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                if(dragging) {
                    stayUntil=System.currentTimeMillis()+60_000L
                    walking=false
                } else {
                    showSpeech(messages.random())
                }
                dragging=false
                return true
            }
        }
        return true
    }

    private fun showSpeech(text:String) {
        val root=effectsRoot?:return
        if(speech==null) {
            speech=TextView(this).apply {
                textSize=14f
                setTextColor(Color.rgb(57,37,33))
                setPadding(18,12,18,12)
                background=GradientDrawable().apply {
                    setColor(Color.rgb(250,246,239))
                    cornerRadius=28f
                    setStroke(2,Color.rgb(91,58,49))
                }
                elevation=8f
                maxWidth=(resources.displayMetrics.widthPixels*0.72f).toInt()
            }
            root.addView(speech,FrameLayout.LayoutParams(-2,-2))
        }
        speech?.text=text
        speech?.visibility=android.view.View.VISIBLE
        val d=resources.displayMetrics.density
        speech?.x=(x*d).coerceAtMost((resources.displayMetrics.widthPixels-260*d).coerceAtLeast(0f))
        speech?.y=((y-58)*d).coerceAtLeast(0f)
        speechUntil=System.currentTimeMillis()+12_000L
    }

    override fun onSharedPreferenceChanged(sharedPreferences:SharedPreferences?,key:String?) {
        readSettings()
        if(key=="size") {
            val d=resources.displayMetrics.density
            params?.width=(side*d).toInt(); params?.height=(side*d).toInt()
            refreshMetrics(); updatePosition()
        }
        if(key=="movement_enabled" && !enabled) walking=false
    }

    override fun onConfigurationChanged(newConfig:android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshMetrics(); updatePosition()
    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        loop?.let{handler.removeCallbacks(it)}
        try{petRoot?.let{wm.removeView(it)}}catch(_:Exception){}
        try{effectsRoot?.let{wm.removeView(it)}}catch(_:Exception){}
        petRoot=null; effectsRoot=null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onBind(intent:Intent?)=null
}
