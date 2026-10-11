// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.nio.charset.StandardCharsets;

public final class ClassPatcher {

    private static final String FROM = "imgui/moulberry90/";
    private static final String TO = "imgui/whaltermc90/";

    private static final String IMGUI = TO + "ImGui";
    private static final String KEY_SHIM = "dev/whaltermc/fba/ImGuiKeyBridge";
    private static final String POPUP_GUARD = "dev/whaltermc/fba/ImGuiPopupGuard";
    /** Flashback Studio actor gizmo: probes ImGui from a mouse callback. */
    private static final String GIZMO_OWNER =
            "com/wsmoothie/flashbackstudio/ui/ActorTransformGizmo";

    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String SAFETY = "dev/whaltermc/fba/GlfwFallback";
    private static final String COMPAT = "dev/whaltermc/fba/GlfwWrapper";

    private static final java.util.Set<String> COMPAT_CALLS = java.util.Set.of(
            "glfwGetWindowAttrib(JI)I",
            "glfwGetMouseButton(JI)I",
            "glfwGetInputMode(JI)I",
            "glfwSetInputMode(JII)V",
            "glfwSetCursorPos(JDD)V",
            "glfwSetCursor(JJ)V",
            "glfwGetCursorPos(J[D[D)V",
            "glfwGetCursorPos(JLjava/nio/DoubleBuffer;Ljava/nio/DoubleBuffer;)V"
    );

    private ClassPatcher() {}

    /**
     * @param classBytes the class being loaded, after mixins have been applied
     * @param flashClass whether the class belongs to Flashback itself. Only
     *                   Flashback's own classes get the FFmpeg call rewrites.
     * @param allowGlfwGuard whether the GLFW guard may run for this class. The
     *                   imgui package rename stays global so addons work, but
     *                   the guard must stay off Minecraft and blaze3d: those
     *                   drive the real window and event loop, and stubbing
     *                   their GLFW calls crashed the game natively on 26.x.
     */
    public static byte[] transform(byte[] classBytes, boolean flashClass,
                                   boolean allowGlfwGuard) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        // The imgui package rename must be applied to EVERY class that mentions
        // it, not just Flashback's. Third party addons (e.g. flashback_studio)
        // are compiled against imgui.moulberry90 and pass imgui types across the
        // addon boundary. Rewriting only the Flashback side would remap a call
        // site descriptor while the callee in the addon kept the old name,
        // producing a NoSuchMethodError at link time.
        boolean hasImGui = contains(classBytes, FROM);
        // The guard still calls real GLFW first and only converts a failing
        // call into a neutral default, so it is safe for Flashback and third
        // party addons. It is NOT safe for Minecraft or blaze3d, which own the
        // real window and must reach GLFW directly.
        boolean hasGlfw = allowGlfwGuard && contains(classBytes, GLFW);
        // The gizmo click guard matches an addon method name, not a package
        // marker. Its only real call site (the MouseHandler the addon injects
        // into) carries no imgui/GLFW bytes, so without this the gate below
        // returns the class unchanged and the guard never runs.
        boolean hasGizmoCall = contains(classBytes, "interceptMouseButton");

        if (!hasImGui && !hasGlfw && !hasGizmoCall) {
            return classBytes;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            ClassWriter writer = new ClassWriter(reader, 0);

            ClassVisitor chain = new GlfwGuard(writer);
            chain = new KeyRedirect(chain);
            chain = new PopupGuard(chain);

            if (hasImGui) {
                chain = new ClassRemapper(chain, new Remapper() {
                    @Override
                    public String map(String internalName) {
                        if (internalName != null && internalName.startsWith(FROM)) {
                            return TO + internalName.substring(FROM.length());
                        }
                        return internalName;
                    }

                    @Override
                    public Object mapValue(Object value) {
                        if (value instanceof String s) {
                            String out = s;
                            if (s.contains("imgui.moulberry90") || s.contains("imgui/moulberry90")) {
                                out = out.replace("imgui.moulberry90", "imgui.whaltermc90")
                                        .replace("imgui/moulberry90", "imgui/whaltermc90");
                            }
                            if (out.contains("GL_ARB_separate_shader_objects : require")) {
                                out = out.replace("GL_ARB_separate_shader_objects : require",
                                        "GL_ARB_separate_shader_objects : warn");
                            }
                            if (!out.equals(s)) {
                                return out;
                            }
                        }
                        return super.mapValue(value);
                    }
                });
            }

            reader.accept(chain, 0);
            return writer.toByteArray();
        } catch (Throwable t) {
            // Never break class loading for another mod — return original bytes.
            return classBytes;
        }
    }

    private static final class PopupGuard extends ClassVisitor {

        PopupGuard(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) return null;

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    // ImGui::IsPopupOpen dereferences GImGui->CurrentWindow, which
                    // is only valid inside a frame. Addons that probe ImGui from
                    // input callbacks would otherwise segfault natively, so route
                    // the call through a guard that answers safely off-frame.
                    if (opcode == Opcodes.INVOKESTATIC && IMGUI.equals(owner)) {
                        // Anchor the frame window on ImGui's own lifecycle calls.
                        // Flashback drives these from ReplayUI, so this tracks
                        // the real frame rather than a backend-specific method.
                        if ("newFrame".equals(mName) && mDesc.equals("()V")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, POPUP_GUARD,
                                    "beginFrame", "()V", false);
                            return;
                        }
                        if ("render".equals(mName) && mDesc.equals("()V")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, POPUP_GUARD,
                                    "endFrame", "()V", false);
                            return;
                        }
                        if ("isPopupOpen".equals(mName)
                                && (mDesc.equals("(Ljava/lang/String;)Z")
                                    || mDesc.equals("(Ljava/lang/String;I)Z"))) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, POPUP_GUARD,
                                    mName, mDesc, false);
                            return;
                        }
                    }
                    // Flashback Studio's gizmo entry answers mouse input by probing
                    // ImGui state (isPopupOpen, then getIO()). getIO() has no
                    // safe off-frame answer. A plain static-call redirect: same
                    // stack shape ([info, action] -> boolean), so max_stack and
                    // frames are untouched. The descriptor is matched loosely on
                    // purpose: intermediary-named builds spell the parameter
                    // type differently, but the owner plus name plus a boolean
                    // result is distinctive enough.
                    if (opcode == Opcodes.INVOKESTATIC
                            && GIZMO_OWNER.equals(owner)
                            && "interceptMouseButton".equals(mName)
                            && mDesc.endsWith(")Z")) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, POPUP_GUARD,
                                "interceptGizmoClick",
                                "(Ljava/lang/Object;I)Z", false);
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }
    }

    private static final class KeyRedirect extends ClassVisitor {

        KeyRedirect(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null) return null;

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && IMGUI.equals(owner) && isKeyCall(mName, mDesc)) {
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, KEY_SHIM, mName, mDesc, false);
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }

        private static boolean isKeyCall(String name, String desc) {
            return switch (name) {
                case "isKeyDown", "isKeyReleased" -> desc.equals("(I)Z");
                case "isKeyPressed" -> desc.equals("(I)Z") || desc.equals("(IZ)Z");
                default -> false;
            };
        }
    }

    // Every other static GLFW call Flashback 0.39.10 makes, mirrored in
    // GlfwFallback. Calls outside both sets keep their owner so new LWJGL3
    // functions work natively on modern launchers.
    private static final java.util.Set<String> SAFE_CALLS = java.util.Set.of(
            "glfwCreateStandardCursor(I)J",
            "glfwCreateWindow(IILjava/lang/CharSequence;JJ)J",
            "glfwDestroyWindow(J)V",
            "glfwFocusWindow(J)V",
            "glfwGetCurrentContext()J",
            "glfwGetFramebufferSize(J[I[I)V",
            "glfwGetGamepadState(ILorg/lwjgl/glfw/GLFWGamepadState;)Z",
            "glfwGetKey(JI)I",
            "glfwGetKeyName(II)Ljava/lang/String;",
            "glfwGetKeyScancode(I)I",
            "glfwGetMonitorContentScale(J[F[F)V",
            "glfwGetMonitorPos(J[I[I)V",
            "glfwGetMonitorWorkarea(J[I[I[I[I)V",
            "glfwGetMonitors()Lorg/lwjgl/PointerBuffer;",
            "glfwGetTime()D",
            "glfwGetVideoMode(J)Lorg/lwjgl/glfw/GLFWVidMode;",
            "glfwGetWindowContentScale(J[F[F)V",
            "glfwGetWindowPos(J[I[I)V",
            "glfwGetWindowSize(J[I[I)V",
            "glfwHideWindow(J)V",
            "glfwMakeContextCurrent(J)V",
            "glfwSetCharModsCallback(JLorg/lwjgl/glfw/GLFWCharModsCallbackI;)Lorg/lwjgl/glfw/GLFWCharModsCallback;",
            "glfwSetCharCallback(JLorg/lwjgl/glfw/GLFWCharCallbackI;)Lorg/lwjgl/glfw/GLFWCharCallback;",
            "glfwGetWindowMonitor(J)J",
            "glfwSetWindowMonitor(JJIIIII)V",
            "glfwPollEvents()V",
            "glfwSetCursorEnterCallback(JLorg/lwjgl/glfw/GLFWCursorEnterCallbackI;)Lorg/lwjgl/glfw/GLFWCursorEnterCallback;",
            "glfwSetCursorPosCallback(JLorg/lwjgl/glfw/GLFWCursorPosCallbackI;)Lorg/lwjgl/glfw/GLFWCursorPosCallback;",
            "glfwSetErrorCallback(Lorg/lwjgl/glfw/GLFWErrorCallbackI;)Lorg/lwjgl/glfw/GLFWErrorCallback;",
            "glfwSetKeyCallback(JLorg/lwjgl/glfw/GLFWKeyCallbackI;)Lorg/lwjgl/glfw/GLFWKeyCallback;",
            "glfwSetMonitorCallback(Lorg/lwjgl/glfw/GLFWMonitorCallbackI;)Lorg/lwjgl/glfw/GLFWMonitorCallback;",
            "glfwSetMouseButtonCallback(JLorg/lwjgl/glfw/GLFWMouseButtonCallbackI;)Lorg/lwjgl/glfw/GLFWMouseButtonCallback;",
            "glfwSetScrollCallback(JLorg/lwjgl/glfw/GLFWScrollCallbackI;)Lorg/lwjgl/glfw/GLFWScrollCallback;",
            "glfwSetWindowCloseCallback(JLorg/lwjgl/glfw/GLFWWindowCloseCallbackI;)Lorg/lwjgl/glfw/GLFWWindowCloseCallback;",
            "glfwSetWindowFocusCallback(JLorg/lwjgl/glfw/GLFWWindowFocusCallbackI;)Lorg/lwjgl/glfw/GLFWWindowFocusCallback;",
            "glfwSetWindowOpacity(JF)V",
            "glfwSetWindowPos(JII)V",
            "glfwSetWindowPosCallback(JLorg/lwjgl/glfw/GLFWWindowPosCallbackI;)Lorg/lwjgl/glfw/GLFWWindowPosCallback;",
            "glfwSetWindowSize(JII)V",
            "glfwSetWindowSizeCallback(JLorg/lwjgl/glfw/GLFWWindowSizeCallbackI;)Lorg/lwjgl/glfw/GLFWWindowSizeCallback;",
            "glfwSetWindowTitle(JLjava/lang/CharSequence;)V",
            "glfwShowWindow(J)V",
            "glfwSwapBuffers(J)V",
            "glfwSwapInterval(I)V",
            "glfwWindowHint(II)V"
    );

    private static final class GlfwGuard extends ClassVisitor {

        private boolean skip;

        GlfwGuard(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            // Never rewrite interfaces or our own classes: the mirrors call
            // real GLFW themselves and must not be rerouted into themselves.
            this.skip = (access & Opcodes.ACC_INTERFACE) != 0
                    || (name != null && name.startsWith("dev/whaltermc/fba/"));
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null || skip) return mv;

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    if (opcode == Opcodes.INVOKESTATIC && GLFW.equals(owner) && !itf) {
                        String key = mName + mDesc;
                        if (COMPAT_CALLS.contains(key)) {
                            super.visitMethodInsn(opcode, COMPAT, mName, mDesc, false);
                            return;
                        }
                        if (SAFE_CALLS.contains(key)) {
                            super.visitMethodInsn(opcode, SAFETY, mName, mDesc, false);
                            return;
                        }
                        // Unknown to both lists: leave it for native LWJGL3.
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }
    }

    private static boolean contains(byte[] data, String ascii) {
        byte[] needle = ascii.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= data.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
