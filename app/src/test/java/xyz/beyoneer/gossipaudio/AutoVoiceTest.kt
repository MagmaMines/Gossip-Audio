package xyz.beyoneer.gossipaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import xyz.beyoneer.gossipaudio.core.AutoVoice

class AutoVoiceTest {
    @Test fun identifiesSupportedSamplesAndRejectsAmbiguity() {
        assertEquals("hi_super", AutoVoice.detect("नमस्ते आपका स्वागत है"))
        assertEquals("ja_super", AutoVoice.detect("こんにちは今日は良い天気です"))
        assertEquals("ko_super", AutoVoice.detect("안녕하세요 반갑습니다"))
        assertEquals("ar_super", AutoVoice.detect("مرحبا بكم"))
        assertEquals("fr_super", AutoVoice.detect("bonjour et bienvenue à tous"))
        assertEquals("es_super", AutoVoice.detect("hola bienvenidos gracias"))
        assertEquals("de_super", AutoVoice.detect("hallo und willkommen"))
        assertEquals("ljspeech", AutoVoice.detect("hello and welcome to your studio"))
        assertNull(AutoVoice.detect("你好世界")) // Han can also be Japanese
        assertNull(AutoVoice.detect("తెలుగు"))
        assertNull(AutoVoice.detect("bonjour hello"))
        assertNull(AutoVoice.detect("123"))
    }
}
