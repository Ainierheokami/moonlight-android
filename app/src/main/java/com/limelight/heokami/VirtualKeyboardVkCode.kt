package com.limelight.heokami

import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.ControllerPacket

object VirtualKeyboardVkCode {
    enum class VKCode(val code: Int) {
        VK_LCONTROL(0xA2),
        VK_RCONTROL(0xA3),

        VK_LSHIFT(0xA0),
        VK_RSHIFT(0xA1),
        VK_LWIN(0x5B),
        VK_RWIN(0x5C),

        VK_LMENU(0xA4),
        VK_RMENU(0xA5),
        VK_ESCAPE(0x1B),
        VK_BACK(0x08),

        VK_SPACE(0x20),
        VK_TAB(0x09),
        VK_CLEAR(0x0C),
        VK_RETURN(0x0D),

        VK_PRIOR(0x21), // page up
        VK_NEXT(0x22), // page down
        VK_END(0x23),
        VK_HOME(0x24),

        VK_LEFT(0x25), // ←
        VK_UP(0x26), // ↑
        VK_RIGHT(0x27), // →
        VK_DOWN(0x28), // ↓


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
        VK_OEM_102(0xDE), // <>

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

        //NUM按键
        VK_NUMPAD0(0x60),
        VK_NUMPAD1(0x61),
        VK_NUMPAD2(0x62),
        VK_NUMPAD3(0x63),
        VK_NUMPAD4(0x64),
        VK_NUMPAD5(0x65),
        VK_NUMPAD6(0x66),
        VK_NUMPAD7(0x67),
        VK_NUMPAD8(0x68),
        VK_NUMPAD9(0x69),
        VK_MULTIPLY(0x6A), // Num_*
        VK_ADD(0x6B), // Num_+
        VK_SEPARATOR(0x6C), // Num_分隔符键
        VK_SUBTRACT(0x6D), // Num_-
        VK_DECIMAL(0x6E), // Num_.
        VK_DIVIDE(0x6F), // Num_/

        VK_PAUSE(0x13),
        VK_NUMLOCK(0x90), // NUM LOCK
        VK_CAPITAL(0x14), // CAPS LOCK
        VK_SCROLL(0x91), // SCROLL LOCK

        VK_SNAPSHOT(0x2C), // PRINT SCREEN
        VK_INSERT(0x2D), // INS 键
        VK_DELETE(0x2E), // DEL 键
        VK_HELP(0x2F), // HELP 键

        // 鼠标
        VK_LBUTTON(0x01),
        VK_RBUTTON(0x02),
        VK_MBUTTON(0x04), // 鼠标中键
        VK_XBUTTON1(0x05), // X1 鼠标按钮
        VK_XBUTTON2(0x06); // X2 鼠标按钮

        companion object { // 添加 companion object
            private val codeMap: Map<Int, VKCode> = entries.associateBy { it.code }

            fun fromCode(code: Int): VKCode? {
                return codeMap[code]
            }
        }
    }

    enum class JoyCode(val code: String) {
        // 手柄
        JOY_A("A"),
        JOY_B("B"),
        JOY_X("X"),
        JOY_Y("Y"),
        JOY_LB("LB"),
        JOY_RB("RB"),
        JOY_BACK("BACK"),
        JOY_START("START"),
        JOY_RT("RT"),
        JOY_LT("LT"),
        JOY_LS("LS"),
        JOY_RS("RS"),
        JOY_PAD("PAD"),
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

    fun replaceVkName(text:String): String{
        var newText = text.replace("VK_","")
        newText = newText.replace("UP","↑")
        newText = newText.replace("DOWN","↓")
        newText = newText.replace("LEFT","←")
        newText = newText.replace("RIGHT","→")
        newText = newText.replace("RETURN","ENTER")
        newText = newText.replace("PRIOR","PAGE_UP")
        newText = newText.replace("NEXT","PAGE_DOWN")
        newText = newText.replace("CAPITAL","CAPS_LOCK")
        newText = newText.replace("SCROLL","SCROLL_LOCK")
        newText = newText.replace("SNAPSHOT","PRINT SCREEN")
        newText = newText.replace("MULTIPLY","NUMPAD_*")
        newText = newText.replace("ADD","NUMPAD_+")
        newText = newText.replace("SEPARATOR","NUMPAD_|")
        newText = newText.replace("SUBTRACT","NUMPAD_-")
        newText = newText.replace("DECIMAL","NUMPAD_.")
        newText = newText.replace("DIVIDE","NUMPAD_/")
        newText = newText.replace("LMENU","LALT")
        newText = newText.replace("RMENU","RALT")
        newText = newText.replace("RBUTTON","RMOUSE")
        newText = newText.replace("LBUTTON","LMOUSE")
        newText = newText.replace("OEM_PLUS","+")
        newText = newText.replace("OEM_COMMA",",")
        newText = newText.replace("OEM_MINUS","-")
        newText = newText.replace("OEM_PERIOD",".")
        newText = newText.replace("OEM_102","<>")
        newText = newText.replace("OEM_1",";:")
        newText = newText.replace("OEM_2","/?")
        newText = newText.replace("OEM_3","~`")
        newText = newText.replace("OEM_4","[{")
        newText = newText.replace("OEM_5","|\\")
        newText = newText.replace("OEM_6","]}")
        newText = newText.replace("OEM_7","'")
        return newText
    }

//    enum class KeyType(val code: Int) {
//        MOUSE(0),
//        KEYBOARD(1),
//        JOYSTICK(2),
//    }

//    fun getKeyType(vkCode: Short): KeyType {
//        return when (vkCode) {
//            VKCode.VK_LBUTTON.code.toShort(),
//            VKCode.VK_RBUTTON.code.toShort(),
//            VKCode.VK_MBUTTON.code.toShort(),
//            VKCode.VK_XBUTTON1.code.toShort(),
//            VKCode.VK_XBUTTON2.code.toShort()
//                -> KeyType.MOUSE
//
//            VKCode.JOY_LB.code.toShort(),
//            VKCode.JOY_RB.code.toShort(),
//            VKCode.JOY_A.code.toShort(),
//            VKCode.JOY_B.code.toShort(),
//            VKCode.JOY_X.code.toShort(),
//            VKCode.JOY_Y.code.toShort(),
//            VKCode.JOY_START.code.toShort(),
//            VKCode.JOY_BACK.code.toShort(),
//            VKCode.JOY_A.code.toShort(),
//            VKCode.JOY_A.code.toShort(),
//                     -> KeyType.JOYSTICK
//
//            else -> KeyType.KEYBOARD
//        }
//    }
}


