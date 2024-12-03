package com.limelight.heokami

import com.limelight.nvstream.input.KeyboardPacket

object VirtualKeyboardVkCode {
    enum class VKCode(val code: Int) {
        VK_LBUTTON(0x01),
        VK_RBUTTON(0x02),
        VK_LCONTROL(0xA2),
        VK_RCONTROL(0xA3),
        VK_LSHIFT(0xA0),
        VK_RSHIFT(0xA1),
        VK_LWIN(0x5B),
        VK_RWIN(0x5C),
        VK_LMENU(0xA4),
        VK_RMENU(0xA5),
        VK_ESCAPE(0x01),
        VK_BACK(0x0E),

        // A-Z
        VK_A(0x41),
        VK_B(0x42),
        VK_C(0x43),
        VK_D(0x44),
        VK_E(0x45),
        VK_F(0x46),
        VK_G(0x47),
        VK_H(0x48),
        VK_I(0x49),
        VK_J(0x4A),
        VK_K(0x4B),
        VK_L(0x4C),
        VK_M(0x4D),
        VK_N(0x4E),
        VK_O(0x4F),
        VK_P(0x50),
        VK_Q(0x51),
        VK_R(0x52),
        VK_S(0x53),
        VK_T(0x54),
        VK_U(0x55),
        VK_V(0x56),
        VK_W(0x57),
        VK_X(0x58),
        VK_Y(0x59),
        VK_Z(0x5A),

        // 特殊标点
        VK_OEM_1(0xBA), // ;:
        VK_OEM_2(0xBF), // /?
        VK_OEM_3(0xC0), // ~`
        VK_OEM_4(0xDB), // [{
        VK_OEM_5(0xDC), // \|
        VK_OEM_6(0xDD), // }]
        VK_OEM_7(0xDE), // '
        VK_OEM_PLUS(0xBB), // +
        VK_OEM_COMMA(0xBC), // ,
        VK_OEM_MINUS(0xBD), // -
        VK_OEM_PERIOD(0xBE), // .
        VK_OEM_102(0xDE), // \|

        // F1~F12
        VK_F1(0x70),
        VK_F2(0x71),
        VK_F3(0x72),
        VK_F4(0x73),
        VK_F5(0x74),
        VK_F6(0x75),
        VK_F7(0x76),
        VK_F8(0x77),
        VK_F9(0x78),
        VK_F10(0x79),
        VK_F11(0x7A),
        VK_F12(0x7B),

    }
    fun replaceSpecialKeys(vkCode: Short): Byte {
        val modifierMask = when (vkCode) {
            VKCode.VK_LCONTROL.code.toShort(), VKCode.VK_RCONTROL.code.toShort() -> KeyboardPacket.MODIFIER_CTRL
            VKCode.VK_LSHIFT.code.toShort(), VKCode.VK_RSHIFT.code.toShort() -> KeyboardPacket.MODIFIER_SHIFT
            VKCode.VK_LMENU.code.toShort(), VKCode.VK_RMENU.code.toShort() -> KeyboardPacket.MODIFIER_ALT
            VKCode.VK_LWIN.code.toShort(), VKCode.VK_RWIN.code.toShort() -> KeyboardPacket.MODIFIER_META
            else -> 0
        }
        return modifierMask.toByte()
    }
}


