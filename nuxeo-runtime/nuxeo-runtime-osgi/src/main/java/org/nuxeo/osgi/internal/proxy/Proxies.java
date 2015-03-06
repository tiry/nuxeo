package org.nuxeo.osgi.internal.proxy;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACC_SUPER;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.V1_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import sun.misc.Unsafe;

public class Proxies {

    public static final Proxies DEFAULT = new Proxies();

    public <T> ProxyFactory<T> specialize(Class<T> type, MethodType methodType, ProxyHandler handler) {
        Class<?> supertype = methodType.returnType();
        if (MethodHandles.lookup().in(supertype).lookupModes() == 0) {
            throw new IllegalArgumentException(
                    "interface " + supertype + " is not visible from " + MethodHandles.lookup());
        }

        String superName = internalname(supertype);
        String proxyName = internalname(Proxies.class.getPackage()).concat("/");
        proxyName = proxyName.concat(supertype.getSimpleName());

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, proxyName, null, internalname(supertype), new String[0]);

        String initDesc = methodType.changeReturnType(void.class).toMethodDescriptorString();
        {
            MethodVisitor init = writer.visitMethod(ACC_PUBLIC, "<init>", initDesc, null, null);
            init.visitCode();
            init.visitVarInsn(ALOAD, 0);
            for (int i = 1; i < methodType.parameterCount(); ++i) {
                init.visitVarInsn(ALOAD, i);
            }
            init.visitMethodInsn(INVOKESPECIAL, superName, "<init>", initDesc, false);
            init.visitInsn(RETURN);
            init.visitMaxs(-1, -1);
            init.visitEnd();

            String factoryDesc = methodType.toMethodDescriptorString();
            MethodVisitor factory = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "0-^-0", factoryDesc, null, null);
            factory.visitCode();
            factory.visitTypeInsn(NEW, proxyName);
            for (int i = 0; i < methodType.parameterCount(); ++i) {
                factory.visitVarInsn(ALOAD, i);
            }
            factory.visitInsn(DUP);
            factory.visitMethodInsn(INVOKESPECIAL, proxyName, "<init>", initDesc, false);
            factory.visitInsn(ARETURN);
            factory.visitMaxs(-1, -1);
            factory.visitEnd();
        }

        String mhPlaceHolder = "<<MH_HOLDER>>";
        int mhHolderCPIndex = writer.newConst(mhPlaceHolder);

        Handle BSM = new Handle(H_INVOKESTATIC, proxyName, "bsm", MethodType.methodType(CallSite.class, Lookup.class,
                String.class, MethodType.class, MethodHandle.class, Method.class).toMethodDescriptorString());
        { // bsm
            MethodVisitor mv = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, "bsm", BSM.getDesc(), null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 3); // mh
            mv.visitVarInsn(ALOAD, 0); // lookup
            mv.visitVarInsn(ALOAD, 2); // method type
            mv.visitVarInsn(ALOAD, 4); // method
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MethodType;Ljava/lang/reflect/Method;)Ljava/lang/invoke/CallSite;",
                    false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }

        Method[] methods = supertype.getDeclaredMethods();
        int[] methodHolderCPIndexes = new int[methods.length];
        for (int methodIndex = 0; methodIndex < methods.length; methodIndex++) {
            Method method = methods[methodIndex];
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                continue;
            }
            // FIXME add support of public methods of java.lang.Object
            if (!Modifier.isAbstract(modifiers) && !handler.override(method)) {
                continue;
            }
            String methodDesc = Type.getMethodDescriptor(method);
            MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, method.getName(), methodDesc, null,
                    internalnames(method.getExceptionTypes()));
            mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
            mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            int slot = 1;
            for (Class<?> parameterType : method.getParameterTypes()) {
                mv.visitVarInsn(Type.getType(parameterType).getOpcode(ILOAD), slot);
                slot += (parameterType == long.class || parameterType == double.class) ? 2 : 1;
            }
            String methodPlaceHolder = "<<METHOD_HOLDER " + methodIndex + ">>";
            methodHolderCPIndexes[methodIndex] = writer.newConst(methodPlaceHolder);
            mv.visitInvokeDynamicInsn(method.getName(), "(Ljava/lang/Object;" + methodDesc.substring(1), BSM,
                    mhPlaceHolder, methodPlaceHolder);
            mv.visitInsn(Type.getReturnType(method).getOpcode(IRETURN));
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
        writer.visitEnd();
        byte[] data = writer.toByteArray();

        try {
            Path path = Files.createTempFile(supertype.getSimpleName(), ".class");
            Files.delete(path);
            Files.copy(new ByteArrayInputStream(data), path);
            System.out.println(path);
        } catch (IOException cause) {
            throw new Error(cause);
        }
        int constantPoolSize = writer.newConst("<<SENTINEL>>");
        Object[] patches = new Object[constantPoolSize];
        patches[mhHolderCPIndex] = MethodHandles.filterReturnValue(context,
                MethodHandles.insertArguments(bootstrap, 0, handler));
        for (int i = 0; i < methodHolderCPIndexes.length; i++) {
            patches[methodHolderCPIndexes[i]] = methods[i];
        }
        Class<?> clazz = unsafe.defineAnonymousClass(type, data, patches);
        unsafe.ensureClassInitialized(clazz);
        try {
            MethodHandle handle = MethodHandles.publicLookup().findStatic(clazz, "0-^-0", methodType);
            return new ProxyFactory<T>(type, handle);
        } catch (ReflectiveOperationException cause) {
            throw new AssertionError(cause);
        }
    }

    public <T> ProxyFactory<T> proxy(Class<T> holder, MethodType methodType,
            ProxyHandler handler) {
        Class<?> interfaze = methodType.returnType();
        if (MethodHandles.lookup().in(interfaze).lookupModes() == 0) {
            throw new IllegalArgumentException("interface " + interfaze + " is not visible");
        }

        String interfazeName = internalname(interfaze);
        String proxyName = internalname(Proxies.class.getPackage()).concat("/");
        proxyName = proxyName.concat(interfaze.getSimpleName());

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_8, ACC_PUBLIC | ACC_SUPER | ACC_FINAL, proxyName, null, "java/lang/Object",
                new String[] { interfazeName });

        String initDesc;
        {
            initDesc = methodType.changeReturnType(void.class).toMethodDescriptorString();
            MethodVisitor init = writer.visitMethod(ACC_PUBLIC, "<init>", initDesc, null, null);
            String factoryDesc = methodType.toMethodDescriptorString();
            MethodVisitor factory = writer.visitMethod(ACC_PUBLIC | ACC_STATIC, "0-^-0", factoryDesc, null, null);
            init.visitCode();
            init.visitVarInsn(ALOAD, 0);
            init.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            factory.visitCode();
            factory.visitTypeInsn(NEW, proxyName);
            factory.visitInsn(DUP);

            int slot = 1;
            for (int i = 0; i < methodType.parameterCount(); i++) {
                Class<?> boundType = methodType.parameterType(i);
                String fieldName = "arg" + i;
                int finalFlag = handler.isMutable(i, boundType) ? 0 : ACC_FINAL;
                FieldVisitor fv = writer.visitField(ACC_PRIVATE | finalFlag, fieldName, Type.getDescriptor(boundType),
                        null, null);
                fv.visitEnd();

                int loadOp = Type.getType(boundType).getOpcode(ILOAD);
                init.visitVarInsn(ALOAD, 0);
                init.visitVarInsn(loadOp, slot);
                init.visitFieldInsn(PUTFIELD, proxyName, fieldName, Type.getDescriptor(boundType));

                factory.visitVarInsn(loadOp, slot - 1);

                slot += (boundType == long.class || boundType == double.class) ? 2 : 1;
            }

            init.visitInsn(RETURN);
            factory.visitMethodInsn(INVOKESPECIAL, proxyName, "<init>", initDesc, false);
            factory.visitInsn(ARETURN);

            init.visitMaxs(-1, -1);
            init.visitEnd();
            factory.visitMaxs(-1, -1);
            factory.visitEnd();
        }

        String mhPlaceHolder = "<<MH_HOLDER>>";
        int mhHolderCPIndex = writer.newConst(mhPlaceHolder);

        Handle BSM = new Handle(H_INVOKESTATIC, proxyName, "bsm", MethodType.methodType(CallSite.class, Lookup.class,
                String.class, MethodType.class, MethodHandle.class, Method.class).toMethodDescriptorString());
        { // bsm
            MethodVisitor mv = writer.visitMethod(ACC_PRIVATE | ACC_STATIC, "bsm", BSM.getDesc(), null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 3); // mh
            mv.visitVarInsn(ALOAD, 0); // lookup
            mv.visitVarInsn(ALOAD, 2); // method type
            mv.visitVarInsn(ALOAD, 4); // method
            mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/invoke/MethodType;Ljava/lang/reflect/Method;)Ljava/lang/invoke/CallSite;",
                    false);
            mv.visitInsn(ARETURN);
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }

        Method[] methods = interfaze.getMethods();
        int[] methodHolderCPIndexes = new int[methods.length];
        for (int methodIndex = 0; methodIndex < methods.length; methodIndex++) {
            Method method = methods[methodIndex];
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers)) {
                continue;
            }
            // FIXME add support of public methods of java.lang.Object
            if (!Modifier.isAbstract(modifiers) && !handler.override(method)) {
                continue;
            }
            String methodDesc = Type.getMethodDescriptor(method);
            MethodVisitor mv = writer.visitMethod(ACC_PUBLIC, method.getName(), methodDesc, null,
                    internalnames(method.getExceptionTypes()));
            mv.visitAnnotation("Ljava/lang/invoke/LambdaForm$Hidden;", true);
            mv.visitAnnotation("Ljava/lang/invoke/ForceInline;", true);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            for (int i = 0; i < methodType.parameterCount(); i++) {
                Class<?> fieldType = methodType.parameterType(i);
                mv.visitVarInsn(ALOAD, 0);
                mv.visitFieldInsn(GETFIELD, proxyName, "arg" + i, Type.getDescriptor(fieldType));
            }
            int slot = 1;
            for (Class<?> parameterType : method.getParameterTypes()) {
                mv.visitVarInsn(Type.getType(parameterType).getOpcode(ILOAD), slot);
                slot += (parameterType == long.class || parameterType == double.class) ? 2 : 1;
            }
            String methodPlaceHolder = "<<METHOD_HOLDER " + methodIndex + ">>";
            methodHolderCPIndexes[methodIndex] = writer.newConst(methodPlaceHolder);
            mv.visitInvokeDynamicInsn(method.getName(),
                    "(Ljava/lang/Object;" + initDesc.substring(1, initDesc.length() - 2) + methodDesc.substring(1), BSM,
                    mhPlaceHolder, methodPlaceHolder);
            mv.visitInsn(Type.getReturnType(method).getOpcode(IRETURN));
            mv.visitMaxs(-1, -1);
            mv.visitEnd();
        }
        writer.visitEnd();
        byte[] data = writer.toByteArray();

        int constantPoolSize = writer.newConst("<<SENTINEL>>");
        Object[] patches = new Object[constantPoolSize];
        patches[mhHolderCPIndex] = MethodHandles.filterReturnValue(context,
                MethodHandles.insertArguments(bootstrap, 0, handler));
        for (int i = 0; i < methodHolderCPIndexes.length; i++) {
            patches[methodHolderCPIndexes[i]] = methods[i];
        }
        Class<?> clazz = unsafe.defineAnonymousClass(interfaze, data, patches);
        unsafe.ensureClassInitialized(clazz);
        try {
            MethodHandle handle = MethodHandles.publicLookup().findStatic(clazz, "0-^-0", methodType);
            return new ProxyFactory<T>(holder, handle);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    final MethodHandle bootstrap = lookupBootstrapHandle();

    MethodHandle lookupBootstrapHandle() {
        try {
            return MethodHandles.lookup().findVirtual(ProxyHandler.class, "bootstrap",
                    MethodType.methodType(CallSite.class, ProxyContext.class));
        } catch (ReflectiveOperationException cause) {
            throw new AssertionError("Cannot lookup bootstrap handle", cause);
        }
    }

    final MethodHandle context = lookupContextHandle();

    MethodHandle lookupContextHandle() {
        try {
            return MethodHandles.lookup().findStatic(ProxyContext.class, "create",
                    MethodType.methodType(ProxyContext.class, Lookup.class, MethodType.class, Method.class));
        } catch (ReflectiveOperationException cause) {
            throw new AssertionError("Cannot lookup context handle", cause);
        }
    }

    final Unsafe unsafe = loadUnsafe();

    Unsafe loadUnsafe() {
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    String internalname(Package pkg) {
        return pkg.getName().replace('.', '/');
    }

    String internalname(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    String[] internalnames(Class<?>[] types) {
        List<String> names = Stream.of(types).map(type -> internalname(type)).collect(Collectors.toList());
        return names.toArray(new String[names.size()]);
    }

}
