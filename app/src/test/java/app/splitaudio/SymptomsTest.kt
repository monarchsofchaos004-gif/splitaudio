package app.splitaudio

import org.junit.Assert.*
import org.junit.Test

class SymptomsTest {
    @Test fun nothingPicked_keepsNote() = assertEquals("no sound", Symptoms.note(emptyList(), " no sound "))
    @Test fun pickedOnly() = assertEquals("Symptoms: Phone calls are affected", Symptoms.note(listOf("calls"), ""))
    @Test fun listOrder_andNote() = assertEquals("Symptoms: Other apps still play on Bluetooth; Phone calls are affected\nhi",
        Symptoms.note(listOf("calls", "bt_others"), "hi"))
    @Test fun unknownKey_ignored() = assertEquals("", Symptoms.note(listOf("nope"), ""))
    @Test fun everyKeyHasLabel() = assertEquals(Symptoms.ALL.map { it.first }.toSet(), Symptoms.LABELS.keys)
    @Test fun keysUnique() = assertEquals(Symptoms.ALL.size, Symptoms.ALL.map { it.first }.toSet().size)
}
