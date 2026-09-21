package io.github.diegog0477.zombiebox.cast

import android.annotation.SuppressLint
import android.media.*
import android.media.projection.MediaProjection
import android.os.SystemClock

/** Android playback capture only. Never selects a microphone input. */
@SuppressLint("NewApi", "MissingPermission")
class Api29PlaybackAudio:PlaybackAudio {
    private var record:AudioRecord?=null
    private var codec:MediaCodec?=null
    @Volatile private var running=false
    private var worker:Thread?=null
    override fun prepare(projection:MediaProjection) {
        try {
            val capture=AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA).addMatchingUsage(AudioAttributes.USAGE_GAME).addMatchingUsage(AudioAttributes.USAGE_UNKNOWN).build()
            val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_STEREO).build()
            val minimum=AudioRecord.getMinBufferSize(44100,AudioFormat.CHANNEL_IN_STEREO,AudioFormat.ENCODING_PCM_16BIT)
            check(minimum>0)
            record=AudioRecord.Builder().setAudioFormat(format).setBufferSizeInBytes(maxOf(minimum,16384)).setAudioPlaybackCaptureConfig(capture).build()
            check(record!!.state==AudioRecord.STATE_INITIALIZED)
            val encoded=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,2).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE,MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE,128000);setInteger(MediaFormat.KEY_MAX_INPUT_SIZE,4096)
            }
            codec=MediaCodec.createEncoderByType("audio/mp4a-latm");codec!!.configure(encoded,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec!!.start()
        } catch(e:Exception){release();throw e}
    }
    override fun start(publisher:RtspPublisher,onFailure:()->Unit) {
        running=true
        worker=Thread({
            try {
                val recorder=record!!;val encoder=codec!!;recorder.startRecording()
                check(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING)
                val pcm=ByteArray(4096);val info=MediaCodec.BufferInfo();var samples=0L
                val origin=System.nanoTime()/1000
                while(running) {
                    val input=encoder.dequeueInputBuffer(1000)
                    if(input>=0) {
                        val count=recorder.read(pcm,0,pcm.size,AudioRecord.READ_NON_BLOCKING)
                        check(count>=0 && count%4==0)
                        val buffer=encoder.getInputBuffer(input)!!;buffer.clear();if(count>0)buffer.put(pcm,0,count)
                        encoder.queueInputBuffer(input,0,count,origin+samples*1000000/44100,0);samples+=count/4
                        if(count==0)SystemClock.sleep(5)
                    }
                    var output=encoder.dequeueOutputBuffer(info,1000)
                    while(output>=0) {
                        try {
                            if(info.size>0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                                val buffer=encoder.getOutputBuffer(output)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size)
                                val data=ByteArray(info.size);buffer.get(data);publisher.audio(data,info.presentationTimeUs)
                            }
                        }finally{encoder.releaseOutputBuffer(output,false)}
                        output=encoder.dequeueOutputBuffer(info,0)
                    }
                }
            } catch(_:Exception){if(running)onFailure()}
            finally {running=false;release()}
        },"zombie-cast-audio").also{it.start()}
    }
    private fun release(){try{record?.stop()}catch(_:Exception){};try{record?.release()}catch(_:Exception){};try{codec?.stop()}catch(_:Exception){};try{codec?.release()}catch(_:Exception){};record=null;codec=null}
    override fun close(){running=false;if(worker==null)release()}
}
