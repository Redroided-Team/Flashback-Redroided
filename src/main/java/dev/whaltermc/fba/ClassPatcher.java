// Copyright (c) 2026 WhalterMC. All Rights Reserved.

package dev.whaltermc.fba;

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

public final class ClassPatcher {

    private static final String FROM = "imgui/moulberry90/";
    private static final String TO = "imgui/whaltermc90/";

    private static final String GLFW = "org/lwjgl/glfw/GLFW";
    private static final String SAFETY = "dev/whaltermc/fba/GlfwFallback";

    // Routes Flashback's ImGui enableScissor call through ScissorCompat, which
    // clamps the empty rects MC 26.3 rejects. Matched by name to stay
    // version-independent.
    private static final String SCISSOR_CLASS =
            "com/moulberry/flashback/editor/ui/CustomImGuiImplB3D";
    private static final String SCISSOR_OWNER =
            "com/mojang/renderpearl/api/commands/RenderPass";
    private static final String SCISSOR_COMPAT =
            "dev/whaltermc/fba/ScissorCompat";

    private ClassPatcher() {}

    /**
     * @param classBytes the class being loaded, after mixins have been applied
     * @param flashClass whether the class belongs to Flashback itself. Only
     *                   Flashback's own classes get the FFmpeg call rewrites;
     *                   the imgui rename and the GLFW guard are deliberately
     *                   global so third party addons work unchanged.
     */
    public static byte[] transform(byte[] classBytes, boolean flashClass) {
        if (classBytes == null || classBytes.length == 0) {
            return classBytes;
        }

        // The imgui rename must cover every class mentioning it: third party
        // addons pass imgui types across the addon boundary, so rewriting only
        // the Flashback side would link to names that no longer exist.
        boolean hasImGui = contains(classBytes, FROM);
        // The GLFW guard is safe for any class: it calls the real GLFW first
        // and only converts failures into a neutral default, so working calls
        // behave identically while Android's missing natives stop crashing.
        boolean hasGlfw = contains(classBytes, GLFW);
        boolean hasScissor = contains(classBytes, "enableScissor");

        if (!hasImGui && !hasGlfw && !hasScissor) {
            return classBytes;
        }

        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, 0);

        ClassVisitor chain = new GlfwGuard(writer);

        if (hasScissor) {
            chain = new ScissorGuard(chain);
        }

        if (hasImGui) {
            Remapper remapper = new Remapper() {

                @Override
                public String map(String internalName) {
                    if (internalName == null) {
                        return null;
                    }

                    if (internalName.startsWith(FROM)) {
                        return TO + internalName.substring(FROM.length());
                    }

                    return internalName;
                }

                @Override
                public Object mapValue(Object value) {
                    if (value instanceof String s) {
                        if (s.contains("imgui.moulberry90")
                                || s.contains("imgui/moulberry90")) {

                            return s
                                    .replace(
                                            "imgui.moulberry90",
                                            "imgui.whaltermc90"
                                    )
                                    .replace(
                                            "imgui/moulberry90",
                                            "imgui/whaltermc90"
                                    );
                        }
                    }

                    return super.mapValue(value);
                }
            };

            chain = new ClassRemapper(chain, remapper);
        }

        reader.accept(chain, 0);

        return writer.toByteArray();
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
            this.skip = isInterface
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
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, GLFW, stub.glfwName(), stub.desc(), false);
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

    /**
     * Rewrites the single {@code RenderPass.enableScissor} call in Flashback's
     * ImGui draw loop to go through {@link ScissorCompat}. Nothing else in the
     * class is touched, and because the replacement takes the pass as its first
     * argument the operand stack at the call site is unchanged -- no frame or
     * max-stack recomputation is needed.
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
                    if (opcode == Opcodes.INVOKEINTERFACE
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

    private static boolean contains(
            byte[] data,
            String ascii
    ) {
        byte[] needle =
                ascii.getBytes(
                        StandardCharsets.US_ASCII
                );

        outer:
        for (
                int i = 0;
                i <= data.length - needle.length;
                i++
        ) {
            for (
                    int j = 0;
                    j < needle.length;
                    j++
            ) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }

            return true;
        }

        return false;
    }
}
