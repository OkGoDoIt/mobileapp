package coredevices.pebble.services

import coredevices.util.recording.RecordingIngress
import coredevices.util.recording.RecordingIngressMetadata
import coredevices.util.recording.RecordingSourceType
import io.rebble.libpebblecommon.packets.Result
import io.rebble.libpebblecommon.services.VoiceService
import io.rebble.libpebblecommon.packets.SessionType
import io.rebble.libpebblecommon.voice.TranscriptionWord
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import io.rebble.libpebblecommon.voice.VoiceSessionIntent
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WatchIndexMemoVoiceSessionHandlerTest {
  @Test
  fun canHandle_onlyIndexMemoIntent() {
    val handler = WatchIndexMemoVoiceSessionHandler(FakeRecordingIngress())
    val indexMemo = request(VoiceSessionIntent.IndexMemo)
    val default = request(VoiceSessionIntent.Default)

    runBlocking {
      assertTrue(handler.canHandle(indexMemo))
      assertFalse(handler.canHandle(default))
    }
  }

  @Test
  fun handle_withoutIngress_returnsDisabled() = runBlocking {
    val handler = WatchIndexMemoVoiceSessionHandler(null)
    val result = handler.handle(request(VoiceSessionIntent.IndexMemo), emptyFlow())

    assertEquals(Result.FailDisabled, result.protocolResult)
    assertEquals(null, result.wordsForWatch)
  }

  @Test
  fun handle_withIngress_finalizesAndReturnsSaved() = runBlocking {
    val ingress = FakeRecordingIngress()
    val handler = WatchIndexMemoVoiceSessionHandler(
      recordingIngress = ingress,
      speexDecoderFactory = { FakeSpeexFrameDecoder(it.sampleRate.toInt()) },
    )

    val result = handler.handle(
      request(VoiceSessionIntent.IndexMemo),
      flowOf(ubyteArrayOf(1u)),
    )

    assertEquals(Result.Success, result.protocolResult)
    assertEquals(listOf(TranscriptionWord("Saved", 1.0f)), result.wordsForWatch)
    assertTrue(ingress.opened)
    assertTrue(ingress.finalized)
    assertFalse(ingress.cancelled)
    assertEquals(RecordingSourceType.PebbleWatchMemo, ingress.lastMetadata?.sourceType)
  }

  @Test
  fun handle_decodeFailure_cancelsRecording() = runBlocking {
    val ingress = FakeRecordingIngress()
    val handler = WatchIndexMemoVoiceSessionHandler(
      recordingIngress = ingress,
      speexDecoderFactory = { FailingSpeexFrameDecoder(it.sampleRate.toInt()) },
    )

    val result = handler.handle(
      request(VoiceSessionIntent.IndexMemo),
      flowOf(ubyteArrayOf(1u)),
    )

    assertEquals(Result.FailRecognizerError, result.protocolResult)
    assertTrue(ingress.cancelled)
    assertFalse(ingress.finalized)
  }

  private fun request(intent: VoiceSessionIntent) = VoiceService.SessionSetupRequest(
    appUuid = Uuid.NIL,
    sessionId = 42,
    sessionType = SessionType.Dictation,
    encoderInfo = VoiceEncoderInfo.Speex(
      sampleRate = 16000,
      version = "1.2rc1",
      bitRate = 12800,
      bitstreamVersion = 4,
      frameSize = 320,
    ),
    sessionIntent = intent,
  )

  private class FakeSpeexFrameDecoder(
    override val sampleRate: Int,
  ) : SpeexFrameDecoder {
    override fun decode(frame: UByteArray): ByteArray = byteArrayOf(0, 1)

    override fun close() {}
  }

  private class FailingSpeexFrameDecoder(
    override val sampleRate: Int,
  ) : SpeexFrameDecoder {
    override fun decode(frame: UByteArray): ByteArray {
      error("decode failed")
    }

    override fun close() {}
  }

  private class FakeRecordingIngress : RecordingIngress {
    var opened = false
    var finalized = false
    var cancelled = false
    var lastMetadata: RecordingIngressMetadata? = null
    val buffer = Buffer()

    override suspend fun openRawPcmSink(
      fileId: String,
      sampleRate: Int,
      mimeType: String,
      metadata: RecordingIngressMetadata,
    ): Sink {
      opened = true
      lastMetadata = metadata
      return buffer
    }

    override suspend fun finalizeLocalRecording(
      fileId: String,
      buttonSequence: String?,
      metadata: RecordingIngressMetadata,
    ) {
      finalized = true
      lastMetadata = metadata
    }

    override suspend fun cancelLocalRecording(fileId: String) {
      cancelled = true
    }
  }
}
