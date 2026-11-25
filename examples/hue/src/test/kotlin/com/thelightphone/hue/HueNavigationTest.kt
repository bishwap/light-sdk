package com.thelightphone.hue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HueNavigationTest {
    @Test
    fun closingManualIpRestoresTheDiscoveredBridgeList() {
        val picker = HueScreenMode.BridgePicker(listOf(DiscoveredBridge("001788FFFE123ABC", "192.168.1.42")))
        val editor = HueUiState(mode = picker).openManualIp()
        assertIs<HueScreenMode.ManualIp>(editor.mode)
        assertEquals(picker, editor.closeManualIp().mode)
    }

    @Test
    fun closingAutomaticManualIpShowsSetupUntilUserReopensTheEditor() {
        val editor = HueUiState(mode = HueScreenMode.Discovering).openManualIp()
        val setup = editor.closeManualIp()
        assertEquals(HueScreenMode.BridgePicker(emptyList()), setup.mode)
        val reopened = setup.openManualIp()
        assertIs<HueScreenMode.ManualIp>(reopened.mode)
        assertEquals(editor.manualIpSession + 1, reopened.manualIpSession)
    }
}
