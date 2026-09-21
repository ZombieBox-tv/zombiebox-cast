package io.github.diegog0477.zombiebox.cast

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.graphics.Color
import android.text.InputType
import android.widget.*
import java.util.concurrent.Executors

@Suppress("DEPRECATION")
class CastActivity:Activity() {
    private val executor=Executors.newSingleThreadExecutor()
    private val handler=Handler()
    private lateinit var repository:GatewayCastRepository
    private lateinit var model:CastViewModel
    private lateinit var receivers:LinearLayout
    private lateinit var status:TextView
    private lateinit var start:Button
    private var consent:Intent?=null
    private lateinit var audio:CheckBox
    private var shareAudio=false
    private var capturePending=false
    private val serviceStatus=android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences,key ->
        if(key=="status" && ::status.isInitialized) {
            status.setText(when(preferences.getString("status","")){"SHARING"->R.string.sharing;"BUFFERING"->R.string.buffering;"FAILED"->R.string.failed;else->R.string.stopped})
            start.isEnabled=!model.state.busy && model.state.selected.isNotEmpty() && !ProjectionService.active
        }
    }
    private var receiverIds=emptyList<String>()
    override fun onCreate(saved:Bundle?) {
        super.onCreate(saved)
        repository=GatewayCastRepository(getSharedPreferences("cast",MODE_PRIVATE))
        val background=executor;val ui=handler
        model=CastViewModel(repository,{work->background.execute{work()}},{done->ui.post{done()}})
        val root=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(32,40,32,24);setBackgroundColor(Color.rgb(10,15,16))}
        fun label(id:Int)=TextView(this).apply {setText(id);setTextColor(Color.WHITE);textSize=18f;root.addView(this)}
        label(R.string.app_name).textSize=28f;label(R.string.tagline)
        val address=EditText(this).apply {setHint(R.string.gateway);setText(repository.api.base);setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);setSingleLine(true);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI}
        val code=EditText(this).apply {setHint(R.string.code);setTextColor(Color.WHITE);setHintTextColor(Color.LTGRAY);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;isSaveEnabled=false;setSingleLine(true)}
        root.addView(address);root.addView(code)
        root.addView(Button(this).apply {setText(R.string.connect);setOnClickListener {val value=code.text.toString();code.setText("");model.connect(address.text.toString(),value)}})
        root.addView(Button(this).apply {setText(R.string.refresh);setOnClickListener {model.refresh()}})
        label(R.string.receivers)
        receivers=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL};root.addView(receivers)
        status=label(R.string.ready)
        label(R.string.consent);label(R.string.audio_detail)
        audio=CheckBox(this).apply{setText(R.string.share_audio);setTextColor(Color.WHITE);isEnabled=Build.VERSION.SDK_INT>=29};root.addView(audio)
        start=Button(this).apply {setText(R.string.start);setOnClickListener {if(!ProjectionService.active) {
            capturePending=true
            shareAudio=audio.isChecked && Build.VERSION.SDK_INT>=29
            if(Build.VERSION.SDK_INT>=29 && shareAudio && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO),102) else captureConsent()
        }}};root.addView(start)
        root.addView(Button(this).apply {setText(R.string.stop);setOnClickListener {stopService(Intent(this@CastActivity,ProjectionService::class.java));status.setText(R.string.stopped)}})
        setContentView(ScrollView(this).apply {addView(root)})
        model.observer={state ->
            start.isEnabled=!state.busy && state.selected.isNotEmpty() && !ProjectionService.active
            status.setText(if(ProjectionService.active)R.string.sharing else if(state.failed)R.string.failed else if(state.busy)R.string.connecting else if(state.receivers.isEmpty())R.string.no_receivers else R.string.ready)
            if(receiverIds!=state.receivers.map{it.id}) {
                receiverIds=state.receivers.map{it.id};receivers.removeAllViews()
                val group=RadioGroup(this)
                for((index,receiver) in state.receivers.withIndex()) group.addView(RadioButton(this).apply{id=index+1;text=receiver.name;setTextColor(Color.WHITE);isChecked=receiver.id==state.selected})
                group.setOnCheckedChangeListener {_,id->state.receivers.getOrNull(id-1)?.let {model.select(it.id)}};receivers.addView(group)
            }
            state.grant?.let {grant ->
                val permission=consent;consent=null;model.consumeGrant()
                if(permission!=null && !ProjectionService.active) {
                    val intent=Intent(this,ProjectionService::class.java).putExtra("audio",shareAudio).putExtra("consent",permission).putExtra("castId",grant.id).putExtra("host",grant.host).putExtra("port",grant.port).putExtra("path",grant.path).putExtra("user",grant.user).putExtra("publishToken",grant.token)
                    try { if(Build.VERSION.SDK_INT>=26)startForegroundService(intent) else startService(intent);status.setText(R.string.buffering);start.isEnabled=false } catch(_:Exception) { executor.execute { try { repository.stop(grant.id) } catch(_:Exception){} };status.setText(R.string.failed) }
                } else executor.execute { try { repository.stop(grant.id) } catch(_:Exception){} }
            }
        }
        if(repository.api.token.isNotEmpty())model.refresh()
    }
    private fun captureConsent(){startActivityForResult((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(),101)}
    override fun onRequestPermissionsResult(request:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(request,permissions,results)
        if(request==102){shareAudio=results.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED;captureConsent()}
    }
    override fun onResume(){super.onResume();getSharedPreferences("cast",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(serviceStatus);if(::model.isInitialized && repository.api.token.isNotEmpty() && !capturePending)model.refresh()}
    override fun onPause(){getSharedPreferences("cast",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(serviceStatus);super.onPause()}
    override fun onActivityResult(request:Int,result:Int,data:Intent?) {super.onActivityResult(request,result,data);if(request==101){capturePending=false;if(result==RESULT_OK && data!=null){consent=data;model.start()}}}
    override fun onDestroy(){model.close();consent=null;executor.shutdown();super.onDestroy()}
}
