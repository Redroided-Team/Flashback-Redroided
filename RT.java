import java.nio.file.*; import org.objectweb.asm.*;
public class RT {
    public static void main(String[] a) throws Exception {
        byte[] in = Files.readAllBytes(Paths.get("test/Caller.class"));
        byte[] out = (byte[]) Class.forName("com.whaltermc.FlashbackTransformer")
                .getMethod("transform", byte[].class, boolean.class, boolean.class)
                .invoke(null, in, false, true);
        // verify: redirect emitted, no branches
        final boolean[] redirected = {false}; final boolean[] branched = {false};
        new ClassReader(out).accept(new ClassVisitor(Opcodes.ASM9){
            public MethodVisitor visitMethod(int ac,String n,String d,String s,String[] e){
                if (!n.equals("onClick")) return null;
                return new MethodVisitor(Opcodes.ASM9){
                    public void visitMethodInsn(int op,String o,String mn,String md,boolean it){
                        if (o.equals("com/whaltermc/ImGuiPopupGuard") && mn.equals("interceptGizmoClick")) redirected[0]=true; }
                    public void visitJumpInsn(int op, org.objectweb.asm.Label l){ branched[0]=true; }
                    public void visitTableSwitchInsn(int min,int max,org.objectweb.asm.Label dflt,org.objectweb.asm.Label... ls){ branched[0]=true; }
                    public void visitLookupSwitchInsn(org.objectweb.asm.Label dflt,int[] k,org.objectweb.asm.Label[] l){ branched[0]=true; }
                };}
        },0);
        System.out.println("redirect emitted: " + redirected[0] + ", new branches: " + branched[0]);
        if (!redirected[0] || branched[0]) { System.out.println("RESULT: FAIL"); System.exit(1); }
        Files.write(Paths.get("test/Caller.class"), out);
        System.out.println("bytecode rewritten OK");

        // runtime: drive the REAL ImGuiPopupGuard through both paths
        Class<?> guard = Class.forName("com.whaltermc.ImGuiPopupGuard");
        java.lang.reflect.Field f = guard.getDeclaredField("frameActive");
        f.setAccessible(true);
        Class<?> mi = Class.forName("net.minecraft.client.input.MouseButtonInfo");
        Object info = mi.getDeclaredConstructor().newInstance();
        java.lang.reflect.Method call = guard.getMethod("interceptGizmoClick", Object.class, int.class);
        f.set(null, false);
        com.wsmoothie.flashbackstudio.ui.ActorTransformGizmo.invoked = false;
        boolean off = (Boolean) call.invoke(null, info, 7);
        System.out.println("off-frame -> " + off + " (stub invoked: " + com.wsmoothie.flashbackstudio.ui.ActorTransformGizmo.invoked + ")");
        f.set(null, true);
        com.wsmoothie.flashbackstudio.ui.ActorTransformGizmo.invoked = false;
        boolean on = (Boolean) call.invoke(null, info, 7);
        System.out.println("on-frame  -> " + on + " (stub invoked: " + com.wsmoothie.flashbackstudio.ui.ActorTransformGizmo.invoked + ")");
        boolean ok = !off && on;
        System.out.println(ok ? "RESULT: PASS" : "RESULT: FAIL");
        if (!ok) System.exit(1);
    }
}
