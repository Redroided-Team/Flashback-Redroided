package com.whaltermc;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FlashbackTransformer {

    private static final String FROM = "imgui/moulberry90/";
    private static final String TO = "imgui/whaltermc90/";

    private static final String IMGUI = TO + "ImGui";
    private static final String KEY_SHIM = "com/whaltermc/ImGuiKeyShim";
    private static final String POPUP_GUARD = "com/whaltermc/ImGuiPopupGuard";
    /** Flashback Studio actor gizmo: probes ImGui from a mouse callback. */
    private static final String GIZMO_OWNER =
            "com/wsmoothie/flashbackstudio/ui/ActorTransformGizmo";
    private static final String GIZMO_CLICK =
            "(Lnet/minecraft/client/input/MouseButtonInfo;I)Z";

    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String SAFETY = "com/whaltermc/GlfwSafety";
    private static final String COMPAT = "com/whaltermc/GlfwCompat";

    // Routes Flashback's ImGui enableScissor call through ScissorCompat, which
    // clamps the empty rects MC 26.2/26.3 rejects. Matched by name to stay
    // version-independent.
    private static final String SCISSOR_CLASS =
            "com/moulberry/flashback/editor/ui/CustomImGuiImplB3D";
    private static final String SCISSOR_OWNER =
            "com/mojang/blaze3d/systems/RenderPass";
    private static final String SCISSOR_COMPAT =
            "com/whaltermc/ScissorCompat";

    /** MC 26.x reads the display name through this exact descriptor. */
    private static final String MONITOR_NAME = "glfwGetMonitorName";
    private static final String MONITOR_NAME_DESC = "(J)Ljava/lang/String;";

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("flashback_android/transform");

    private static final java.util.Set<String> COMPAT_CALLS = java.util.Set.of(
            "glfwGetWindowAttrib(JI)I",
            "glfwGetMouseButton(JI)I",
            "glfwGetInputMode(JI)I",
            "glfwGetCursorPos(J[D[D)V",
            "glfwGetCursorPos(JLjava/nio/DoubleBuffer;Ljava/nio/DoubleBuffer;)V",
            // Cursor/mode/shape writes go through GlfwCompat (mobile touch
            // handling); without this touch input reaches Flashback raw.
            "glfwSetCursorPos(JDD)V",
            // Mode and shape writes go through GlfwCompat as well, like 1.21.
            "glfwSetInputMode(JII)V",
            "glfwSetCursor(JJ)V"
    );

    // Content-scale answers feed Flashback's UI scale directly: a generated
    // stub can only hand back zeros there, so these two go through GlfwSafety,
    // which substitutes the device display density instead (AndroidDisplay).
    private static final java.util.Set<String> CONTENT_SCALE_CALLS = java.util.Set.of(
            "glfwGetWindowContentScale(J[F[F)V",
            "glfwGetMonitorContentScale(J[F[F)V"
    );

    private static final String FFMPEG_PKG = "org/bytedeco/ffmpeg/";
    private static final String FF_COMPAT = "com/whaltermc/FFmpegCompat";
    private static final String CTX = "org/bytedeco/ffmpeg/avcodec/AVCodecContext";
    private static final String FRAME = "org/bytedeco/ffmpeg/avutil/AVFrame";
    private static final String AVUTIL = "org/bytedeco/ffmpeg/global/avutil";
    private static final String SWR = "org/bytedeco/ffmpeg/global/swresample";
    private static final String SWR_CTX = "org/bytedeco/ffmpeg/swresample/SwrContext";

    private record FfRule(boolean virtual, String compatName) {}

    private static final java.util.Map<String, FfRule> FF_RULES = java.util.Map.ofEntries(
            java.util.Map.entry(CTX + ".channels(I)L" + CTX + ";", new FfRule(true, "ctxSetChannels")),
            java.util.Map.entry(CTX + ".channels()I", new FfRule(true, "ctxGetChannels")),
            java.util.Map.entry(CTX + ".channel_layout(J)L" + CTX + ";", new FfRule(true, "ctxSetLayout")),
            java.util.Map.entry(CTX + ".channel_layout()J", new FfRule(true, "ctxGetLayout")),
            java.util.Map.entry(FRAME + ".channels(I)L" + FRAME + ";", new FfRule(true, "frameSetChannels")),
            java.util.Map.entry(FRAME + ".channels()I", new FfRule(true, "frameGetChannels")),
            java.util.Map.entry(FRAME + ".channel_layout(J)L" + FRAME + ";", new FfRule(true, "frameSetLayout")),
            java.util.Map.entry(FRAME + ".channel_layout()J", new FfRule(true, "frameGetLayout")),
            java.util.Map.entry(AVUTIL + ".av_get_default_channel_layout(I)J", new FfRule(false, "defaultLayout")),
            java.util.Map.entry(AVUTIL + ".av_get_channel_layout_nb_channels(J)I", new FfRule(false, "nbChannels")),
            java.util.Map.entry(SWR + ".swr_alloc_set_opts(L" + SWR_CTX + ";JIIJIIILorg/bytedeco/javacpp/Pointer;)L" + SWR_CTX + ";",
                    new FfRule(false, "swrAllocSetOpts"))
    );

    private FlashbackTransformer() {}

    /**
     * @param classBytes the class being loaded, after mixins have been applied
     * @param flashClass whether the class belongs to Flashback itself. Only
     *                   Flashback's own classes get the FFmpeg call rewrites.
     * @param allowGlfwGuard whether the GLFW guard may run for this class.
     *                   It must stay off Minecraft and blaze3d: those own the
     *                   real window, and stubbing them crashed the game natively.
     */
    public static byte[] transform(byte[] classBytes, boolean flashClass,
                                   boolean allowGlfwGuard) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        ClassReader probe = new ClassReader(classBytes);
        String probeName = probe.getClassName();

        // LWJGL's own classes are never rewritten: the guard targets callers,
        // and perturbing the library itself is pure downside.
        if (probeName != null && probeName.startsWith("org/lwjgl/")) {
            if (GLFW.equals(probeName)) {
                return flashbackRedroided$addMonitorNameShim(classBytes, probe);
            }
            return classBytes;
        }

        // The imgui rename must cover every class mentioning it, or descriptors
        // mismatch across the addon boundary (NoSuchMethodError at link time).
        boolean hasImGui = contains(classBytes, FROM);
        // The guard calls real GLFW first and only neutralises failures, so it
        // is safe for Flashback and addons — but not for Minecraft/blaze3d.
        boolean hasGlfw = allowGlfwGuard && contains(classBytes, GLFW);
        // FFmpeg rewrites stay Flashback-only: forcing them onto unrelated
        // classes sharing the javacpp dependency would be riskier.
        boolean hasFfmpeg = flashClass && contains(classBytes, FFMPEG_PKG);
        // The gizmo guard matches by method name: its only call site carries no
        // imgui/GLFW/ffmpeg bytes, so the gate below would otherwise skip it.
        boolean hasGizmoCall = contains(classBytes, "interceptMouseButton");
        boolean hasScissor = contains(classBytes, "enableScissor");

        if (!hasImGui && !hasGlfw && !hasFfmpeg && !hasGizmoCall && !hasScissor) {
            return classBytes;
        }

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, 0);

        ClassVisitor chain = new GlfwGuard(writer);
        chain = new FfmpegRedirect(chain);
        chain = new KeyRedirect(chain);
        chain = new PopupGuard(chain);

        if (hasScissor) {
            chain = new ScissorGuard(chain);
        }

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
                    if (value instanceof String s
                            && (s.contains("imgui.moulberry90") || s.contains("imgui/moulberry90"))) {
                        return s.replace("imgui.moulberry90", "imgui.whaltermc90")
                                .replace("imgui/moulberry90", "imgui/whaltermc90");
                    }
                    return super.mapValue(value);
                }
            });
        }

        reader.accept(chain, 0);
        return writer.toByteArray();
    }

    /**
     * Supplies {@code glfwGetMonitorName(long)} when the loaded GLFW lacks it:
     * MC 26.x calls it from {@code Monitor}, and a launcher LWJGL missing that
     * descriptor dies before the first window opens. Only added when genuinely
     * absent, so healthy LWJGL builds are untouched.
     */
    private static byte[] flashbackRedroided$addMonitorNameShim(
            byte[] classBytes, ClassReader reader) {
        boolean[] present = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (MONITOR_NAME.equals(name) && MONITOR_NAME_DESC.equals(descriptor)) {
                    present[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (present[0]) {
            return classBytes;
        }

        LOGGER.info("FBA: GLFW is missing {}{}, supplying a safe implementation",
                MONITOR_NAME, MONITOR_NAME_DESC);

        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visitEnd() {
                // Branch-free body: no StackMapTable needed under the
                // non-computing ClassWriter above.
                MethodVisitor mv = super.visitMethod(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                        MONITOR_NAME, MONITOR_NAME_DESC, null, null);
                mv.visitCode();
                mv.visitVarInsn(Opcodes.LLOAD, 0);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, COMPAT,
                        MONITOR_NAME, MONITOR_NAME_DESC, false);
                mv.visitInsn(Opcodes.ARETURN);
                // (J) takes two local slots; maxLocals=1 fails defineClass on
                // exactly the launchers this shim exists for.
                mv.visitMaxs(2, 2);
                mv.visitEnd();
                super.visitEnd();
            }
        }, 0);
        return writer.toByteArray();
    }

    /**
     * Routes the single {@code RenderPass.enableScissor} call in Flashback's
     * ImGui draw loop through {@link ScissorCompat}. The replacement takes the
     * pass first, so the call-site stack is unchanged (no frame recompute).
     */
    private static final class ScissorGuard extends ClassVisitor {

        private boolean target;

        ScissorGuard(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.target = SCISSOR_CLASS.equals(name);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (mv == null || !target || !"renderDrawData".equals(name)) {
                return mv;
            }

            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String mName,
                                            String mDesc, boolean itf) {
                    if (opcode == Opcodes.INVOKEVIRTUAL
                            && SCISSOR_OWNER.equals(owner)
                            && "enableScissor".equals(mName)
                            && "(IIII)V".equals(mDesc)) {

                        super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                SCISSOR_COMPAT,
                                "enableScissor",
                                "(L" + SCISSOR_OWNER + ";IIII)V",
                                false
                        );
                        return;
                    }

                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
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
                    // IsPopupOpen dereferences the current window, valid only
                    // inside a frame: route off-frame addon probes through a
                    // guard that answers safely instead of segfaulting.
                    if (opcode == Opcodes.INVOKESTATIC && IMGUI.equals(owner)) {
                        // Anchor the frame window on ImGui's own lifecycle calls.
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
                    // Flashback Studio's gizmo click probes ImGui state with no
                    // frame to hand it to: decline early with the same stack
                    // shape, so verification (original max_stack) still holds.
                    // (Emitting branches here once broke verification.)
                    if (opcode == Opcodes.INVOKESTATIC
                            && GIZMO_OWNER.equals(owner)
                            && "interceptMouseButton".equals(mName)
                            && GIZMO_CLICK.equals(mDesc)) {
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

    private static final class FfmpegRedirect extends ClassVisitor {

        FfmpegRedirect(ClassVisitor next) {
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
                    if (owner.startsWith(FFMPEG_PKG)
                            && (opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKESTATIC)) {
                        FfRule rule = FF_RULES.get(owner + "." + mName + mDesc);
                        if (rule != null && rule.virtual() == (opcode == Opcodes.INVOKEVIRTUAL)) {
                            String newDesc = rule.virtual()
                                    ? "(L" + owner + ";" + mDesc.substring(1)
                                    : mDesc;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, FF_COMPAT,
                                    rule.compatName(), newDesc, false);
                            return;
                        }
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }
    }

    private record Stub(String glfwName, String desc, String stubName) {}

    private static final class GlfwGuard extends ClassVisitor {

        private final Map<String, Stub> stubs = new LinkedHashMap<>();
        private boolean isInterface;
        private String className;
        private boolean skip;

        GlfwGuard(ClassVisitor next) {
            super(Opcodes.ASM9, next);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.className = name;
            this.isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            // Never inject stubs into interfaces, annotations, or our classes.
            this.skip = isInterface
                    || (name != null && name.startsWith("com/whaltermc/"));
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
                        // Same descriptor, same stack shape: only the owner
                        // changes, so no frame recompute is needed.
                        if (CONTENT_SCALE_CALLS.contains(mName + mDesc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, SAFETY, mName, mDesc, false);
                            return;
                        }
                        Stub stub = stubs.computeIfAbsent(mName + mDesc,
                                k -> new Stub(mName, mDesc,
                                        "flashback$redroided$glfw$" + mName + "$" + stubs.size()));
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, className,
                                stub.stubName(), stub.desc(), isInterface);
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, mName, mDesc, itf);
                }
            };
        }

        @Override
        public void visitEnd() {
            for (Stub stub : stubs.values()) {
                emitStub(stub);
            }
            super.visitEnd();
        }

        private void emitStub(Stub stub) {
            Type[] args = Type.getArgumentTypes(stub.desc());
            Type ret = Type.getReturnType(stub.desc());

            MethodVisitor mv = super.visitMethod(
                    Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                    stub.stubName(), stub.desc(), null, null);
            mv.visitCode();

            Label start = new Label();
            Label end = new Label();
            Label handler = new Label();
            mv.visitTryCatchBlock(start, end, handler, "java/lang/LinkageError");
            mv.visitTryCatchBlock(start, end, handler, "java/lang/RuntimeException");

            mv.visitLabel(start);
            int slot = 0;
            Object[] frameLocals = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                mv.visitVarInsn(args[i].getOpcode(Opcodes.ILOAD), slot);
                slot += args[i].getSize();
                frameLocals[i] = frameType(args[i]);
            }
            String target = COMPAT_CALLS.contains(stub.glfwName() + stub.desc()) ? COMPAT : GLFW;
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, target, stub.glfwName(), stub.desc(), false);
            mv.visitLabel(end);
            mv.visitInsn(returnOpcode(ret));

            mv.visitLabel(handler);
            mv.visitFrame(Opcodes.F_NEW, frameLocals.length, frameLocals,
                    1, new Object[]{"java/lang/Throwable"});
            mv.visitVarInsn(Opcodes.ASTORE, slot);
            mv.visitLdcInsn(stub.glfwName());
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, SAFETY, "missing",
                    "(Ljava/lang/String;Ljava/lang/Throwable;)V", false);
            pushZero(mv, ret);
            mv.visitInsn(returnOpcode(ret));

            mv.visitMaxs(slot + 2, slot + 1);
            mv.visitEnd();
        }

        private static Object frameType(Type t) {
            return switch (t.getSort()) {
                case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.INTEGER;
                case Type.FLOAT -> Opcodes.FLOAT;
                case Type.LONG -> Opcodes.LONG;
                case Type.DOUBLE -> Opcodes.DOUBLE;
                case Type.ARRAY -> t.getDescriptor();
                default -> t.getInternalName();
            };
        }

        private static int returnOpcode(Type ret) {
            return ret.getSort() == Type.VOID ? Opcodes.RETURN : ret.getOpcode(Opcodes.IRETURN);
        }

        private static void pushZero(MethodVisitor mv, Type ret) {
            switch (ret.getSort()) {
                case Type.VOID -> { }
                case Type.LONG -> mv.visitInsn(Opcodes.LCONST_0);
                case Type.FLOAT -> mv.visitInsn(Opcodes.FCONST_0);
                case Type.DOUBLE -> mv.visitInsn(Opcodes.DCONST_0);
                case Type.OBJECT, Type.ARRAY -> mv.visitInsn(Opcodes.ACONST_NULL);
                default -> mv.visitInsn(Opcodes.ICONST_0);
            }
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
