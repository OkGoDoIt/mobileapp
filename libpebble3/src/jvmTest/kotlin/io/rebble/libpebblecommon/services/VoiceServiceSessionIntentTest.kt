package io.rebble.libpebblecommon.services

import io.rebble.libpebblecommon.packets.SessionSetupCommand
import io.rebble.libpebblecommon.packets.VoiceAttribute
import io.rebble.libpebblecommon.packets.VoiceAttributeType
import io.rebble.libpebblecommon.packets.VoiceCommand
import io.rebble.libpebblecommon.packets.voicePacketsRegister
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.voice.VoiceEncoderInfo
import io.rebble.libpebblecommon.voice.VoiceSessionIntent
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VoiceServiceSessionIntentTest {
    @BeforeTest
    fun setUp() {
        voicePacketsRegister()
    }

    @Test
    fun sessionSetupWithoutIntentHasNoSessionIntentAttribute() {
        val packet = buildSessionSetup(intent = null)
        val parsed = PebblePacket.deserialize(packet) as SessionSetupCommand
        val attributes = parsed.attributes.list
        assertNotNull(VoiceEncoderInfo.fromProtocol(attributes))
        val intentAttr = attributes.firstOrNull { attr ->
            attr.id.get() == VoiceAttributeType.SessionIntent.value
        }
        assertNull(intentAttr)
    }

    @Test
    fun sessionSetupWithIndexMemoIntent() {
        val packet = buildSessionSetup(intent = VoiceSessionIntent.IndexMemo.wireValue)
        val parsed = PebblePacket.deserialize(packet) as SessionSetupCommand
        val intentData = parsed.attributes.list.firstOrNull { attr ->
            attr.id.get() == VoiceAttributeType.SessionIntent.value
        }?.content?.get()
        assertNotNull(intentData)
        assertEquals(VoiceSessionIntent.IndexMemo, VoiceSessionIntent.fromWireValue(intentData[0]))
    }

    @Test
    fun unknownIntentDoesNotCrash() {
        val intent = VoiceSessionIntent.fromWireValue(0x7fu)
        assertIs<VoiceSessionIntent.Unknown>(intent)
        assertEquals(0x7fu, (intent as VoiceSessionIntent.Unknown).raw)
    }

    private fun buildSessionSetup(intent: UByte?): UByteArray {
        val speex = VoiceAttribute.SpeexEncoderInfo().apply {
            version.set("1.2rc1")
            sampleRate.set(16000u)
            bitRate.set(12800u)
            bitstreamVersion.set(4u)
            frameSize.set(320u)
        }
        val attrList = buildList {
            if (intent != null) {
                add(
                    VoiceAttribute(
                        id = VoiceAttributeType.SessionIntent.value,
                        content = VoiceAttribute.SessionIntent(intent),
                    ),
                )
            }
            add(VoiceAttribute(VoiceAttributeType.SpeexEncoderInfo.value, speex))
        }
        val packet = SessionSetupCommand().apply {
            command.set(VoiceCommand.SessionSetup.value)
            sessionType.set(0x01u)
            sessionId.set(7u)
            attributeCount.set(attrList.size.toUByte())
            attributes.count = attrList.size
            attributes.list = attrList
        }
        return packet.serialize()
    }
}
