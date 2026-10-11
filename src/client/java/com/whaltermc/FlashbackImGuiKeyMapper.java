package com.whaltermc;

import imgui.whaltermc90.flag.ImGuiKey;
import org.lwjgl.glfw.GLFW;

public final class FlashbackImGuiKeyMapper {

    private FlashbackImGuiKeyMapper() {}

    private static final int OLD_SHIFT_BEGIN = 584;
    private static final int OLD_SHIFT_END = 616;
    private static final int OLD_TO_NEW_SHIFT = 12;
    private static final int IMGUI_MOD_BEGIN = 1 << 12;

    public static int map(int key) {
        if (key >= IMGUI_MOD_BEGIN) {
            return key;
        }

        if (key >= OLD_SHIFT_BEGIN && key <= OLD_SHIFT_END) {
            return key + OLD_TO_NEW_SHIFT;
        }

        if (key >= ImGuiKey.NamedKey_BEGIN && key < OLD_SHIFT_BEGIN) {
            return key;
        }

        return switch (key) {
            case GLFW.GLFW_KEY_TAB -> ImGuiKey.Tab;
            case GLFW.GLFW_KEY_LEFT -> ImGuiKey.LeftArrow;
            case GLFW.GLFW_KEY_RIGHT -> ImGuiKey.RightArrow;
            case GLFW.GLFW_KEY_UP -> ImGuiKey.UpArrow;
            case GLFW.GLFW_KEY_DOWN -> ImGuiKey.DownArrow;
            case GLFW.GLFW_KEY_PAGE_UP -> ImGuiKey.PageUp;
            case GLFW.GLFW_KEY_PAGE_DOWN -> ImGuiKey.PageDown;
            case GLFW.GLFW_KEY_HOME -> ImGuiKey.Home;
            case GLFW.GLFW_KEY_END -> ImGuiKey.End;
            case GLFW.GLFW_KEY_INSERT -> ImGuiKey.Insert;
            case GLFW.GLFW_KEY_DELETE -> ImGuiKey.Delete;
            case GLFW.GLFW_KEY_BACKSPACE -> ImGuiKey.Backspace;
            case GLFW.GLFW_KEY_SPACE -> ImGuiKey.Space;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> ImGuiKey.Enter;
            case GLFW.GLFW_KEY_ESCAPE -> ImGuiKey.Escape;

            case GLFW.GLFW_KEY_LEFT_CONTROL -> ImGuiKey.LeftCtrl;
            case GLFW.GLFW_KEY_LEFT_SHIFT -> ImGuiKey.LeftShift;
            case GLFW.GLFW_KEY_LEFT_ALT -> ImGuiKey.LeftAlt;
            case GLFW.GLFW_KEY_LEFT_SUPER -> ImGuiKey.LeftSuper;
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> ImGuiKey.RightCtrl;
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> ImGuiKey.RightShift;
            case GLFW.GLFW_KEY_RIGHT_ALT -> ImGuiKey.RightAlt;
            case GLFW.GLFW_KEY_RIGHT_SUPER -> ImGuiKey.RightSuper;
            case GLFW.GLFW_KEY_MENU -> ImGuiKey.Menu;

            case GLFW.GLFW_KEY_0 -> ImGuiKey._0;
            case GLFW.GLFW_KEY_1 -> ImGuiKey._1;
            case GLFW.GLFW_KEY_2 -> ImGuiKey._2;
            case GLFW.GLFW_KEY_3 -> ImGuiKey._3;
            case GLFW.GLFW_KEY_4 -> ImGuiKey._4;
            case GLFW.GLFW_KEY_5 -> ImGuiKey._5;
            case GLFW.GLFW_KEY_6 -> ImGuiKey._6;
            case GLFW.GLFW_KEY_7 -> ImGuiKey._7;
            case GLFW.GLFW_KEY_8 -> ImGuiKey._8;
            case GLFW.GLFW_KEY_9 -> ImGuiKey._9;

            case GLFW.GLFW_KEY_A -> ImGuiKey.A;
            case GLFW.GLFW_KEY_B -> ImGuiKey.B;
            case GLFW.GLFW_KEY_C -> ImGuiKey.C;
            case GLFW.GLFW_KEY_D -> ImGuiKey.D;
            case GLFW.GLFW_KEY_E -> ImGuiKey.E;
            case GLFW.GLFW_KEY_F -> ImGuiKey.F;
            case GLFW.GLFW_KEY_G -> ImGuiKey.G;
            case GLFW.GLFW_KEY_H -> ImGuiKey.H;
            case GLFW.GLFW_KEY_I -> ImGuiKey.I;
            case GLFW.GLFW_KEY_J -> ImGuiKey.J;
            case GLFW.GLFW_KEY_K -> ImGuiKey.K;
            case GLFW.GLFW_KEY_L -> ImGuiKey.L;
            case GLFW.GLFW_KEY_M -> ImGuiKey.M;
            case GLFW.GLFW_KEY_N -> ImGuiKey.N;
            case GLFW.GLFW_KEY_O -> ImGuiKey.O;
            case GLFW.GLFW_KEY_P -> ImGuiKey.P;
            case GLFW.GLFW_KEY_Q -> ImGuiKey.Q;
            case GLFW.GLFW_KEY_R -> ImGuiKey.R;
            case GLFW.GLFW_KEY_S -> ImGuiKey.S;
            case GLFW.GLFW_KEY_T -> ImGuiKey.T;
            case GLFW.GLFW_KEY_U -> ImGuiKey.U;
            case GLFW.GLFW_KEY_V -> ImGuiKey.V;
            case GLFW.GLFW_KEY_W -> ImGuiKey.W;
            case GLFW.GLFW_KEY_X -> ImGuiKey.X;
            case GLFW.GLFW_KEY_Y -> ImGuiKey.Y;
            case GLFW.GLFW_KEY_Z -> ImGuiKey.Z;

            case GLFW.GLFW_KEY_F1 -> ImGuiKey.F1;
            case GLFW.GLFW_KEY_F2 -> ImGuiKey.F2;
            case GLFW.GLFW_KEY_F3 -> ImGuiKey.F3;
            case GLFW.GLFW_KEY_F4 -> ImGuiKey.F4;
            case GLFW.GLFW_KEY_F5 -> ImGuiKey.F5;
            case GLFW.GLFW_KEY_F6 -> ImGuiKey.F6;
            case GLFW.GLFW_KEY_F7 -> ImGuiKey.F7;
            case GLFW.GLFW_KEY_F8 -> ImGuiKey.F8;
            case GLFW.GLFW_KEY_F9 -> ImGuiKey.F9;
            case GLFW.GLFW_KEY_F10 -> ImGuiKey.F10;
            case GLFW.GLFW_KEY_F11 -> ImGuiKey.F11;
            case GLFW.GLFW_KEY_F12 -> ImGuiKey.F12;
            case GLFW.GLFW_KEY_F13 -> ImGuiKey.F13;
            case GLFW.GLFW_KEY_F14 -> ImGuiKey.F14;
            case GLFW.GLFW_KEY_F15 -> ImGuiKey.F15;
            case GLFW.GLFW_KEY_F16 -> ImGuiKey.F16;
            case GLFW.GLFW_KEY_F17 -> ImGuiKey.F17;
            case GLFW.GLFW_KEY_F18 -> ImGuiKey.F18;
            case GLFW.GLFW_KEY_F19 -> ImGuiKey.F19;
            case GLFW.GLFW_KEY_F20 -> ImGuiKey.F20;
            case GLFW.GLFW_KEY_F21 -> ImGuiKey.F21;
            case GLFW.GLFW_KEY_F22 -> ImGuiKey.F22;
            case GLFW.GLFW_KEY_F23 -> ImGuiKey.F23;
            case GLFW.GLFW_KEY_F24 -> ImGuiKey.F24;

            case GLFW.GLFW_KEY_APOSTROPHE -> ImGuiKey.Apostrophe;
            case GLFW.GLFW_KEY_COMMA -> ImGuiKey.Comma;
            case GLFW.GLFW_KEY_MINUS -> ImGuiKey.Minus;
            case GLFW.GLFW_KEY_PERIOD -> ImGuiKey.Period;
            case GLFW.GLFW_KEY_SLASH -> ImGuiKey.Slash;
            case GLFW.GLFW_KEY_SEMICOLON -> ImGuiKey.Semicolon;
            case GLFW.GLFW_KEY_EQUAL -> ImGuiKey.Equal;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> ImGuiKey.LeftBracket;
            case GLFW.GLFW_KEY_BACKSLASH -> ImGuiKey.Backslash;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> ImGuiKey.RightBracket;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> ImGuiKey.GraveAccent;
            case GLFW.GLFW_KEY_CAPS_LOCK -> ImGuiKey.CapsLock;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> ImGuiKey.ScrollLock;
            case GLFW.GLFW_KEY_NUM_LOCK -> ImGuiKey.NumLock;
            case GLFW.GLFW_KEY_PRINT_SCREEN -> ImGuiKey.PrintScreen;
            case GLFW.GLFW_KEY_PAUSE -> ImGuiKey.Pause;

            case GLFW.GLFW_KEY_KP_0 -> ImGuiKey.Keypad0;
            case GLFW.GLFW_KEY_KP_1 -> ImGuiKey.Keypad1;
            case GLFW.GLFW_KEY_KP_2 -> ImGuiKey.Keypad2;
            case GLFW.GLFW_KEY_KP_3 -> ImGuiKey.Keypad3;
            case GLFW.GLFW_KEY_KP_4 -> ImGuiKey.Keypad4;
            case GLFW.GLFW_KEY_KP_5 -> ImGuiKey.Keypad5;
            case GLFW.GLFW_KEY_KP_6 -> ImGuiKey.Keypad6;
            case GLFW.GLFW_KEY_KP_7 -> ImGuiKey.Keypad7;
            case GLFW.GLFW_KEY_KP_8 -> ImGuiKey.Keypad8;
            case GLFW.GLFW_KEY_KP_9 -> ImGuiKey.Keypad9;
            case GLFW.GLFW_KEY_KP_DECIMAL -> ImGuiKey.KeypadDecimal;
            case GLFW.GLFW_KEY_KP_DIVIDE -> ImGuiKey.KeypadDivide;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> ImGuiKey.KeypadMultiply;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> ImGuiKey.KeypadSubtract;
            case GLFW.GLFW_KEY_KP_ADD -> ImGuiKey.KeypadAdd;
            case GLFW.GLFW_KEY_KP_EQUAL -> ImGuiKey.KeypadEqual;

            default -> ImGuiKey.None;
        };
    }
}
