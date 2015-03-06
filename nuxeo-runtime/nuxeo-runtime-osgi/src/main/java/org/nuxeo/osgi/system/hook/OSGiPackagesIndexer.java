package org.nuxeo.osgi.system.hook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.trycompanion.TryCompanion;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

class OSGiPackagesIndexer {
    final Map<String, Package> pkgs = new HashMap<>();

    void index(DirectoryStream<Path> classes) throws IOException {
        TryCompanion.<Void> of(IOException.class)
                .sneakyForEachAndCollect(
                        StreamSupport.stream(classes.spliterator(), false),
                        this::index)
                .orElseThrow(() -> new IOException("Cannot index some classes"));
    }

    void index(Path path) {
        Package pkg = pkgs.computeIfAbsent(pkgname(path), pkgname -> new Package(pkgname));
        try {
            new ClassAnalyzer(pkg).analyze(Files.newInputStream(path, StandardOpenOption.READ));
        } catch (IOException cause) {
            LogFactory.getLog(OSGiPackagesIndexer.class)
                    .error("Cannot analyze " + path, cause);
        }
    }

    String exports() {
        return pkgs.values()
                .stream()
                .map(pkg -> pkg.name)
                .collect(Collectors.joining(","));
    }

    String imports() {
        return pkgs.values()
                .stream()
                .flatMap(pkg -> pkg.uses.stream())
                .distinct()
                .collect(Collectors.joining(","));
    }

    String pkgname(Path path) {
        return path.getRoot()
                .relativize(path.getParent())
                .toString()
                .replace('/', '.');
    }

    class Package {

        Package(String name) {
            this.name = name;
        }

        final String name;

        final Set<String> uses = new HashSet<String>();

        void noteUse(String classname) {
            String name = classname.substring(0, classname.lastIndexOf('/'))
                    .replace('/', '.');
            uses.add(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    class ClassAnalyzer {

        ClassAnalyzer(Package pkg) {
            this.pkg = pkg;
        }

        final Package pkg;

        void addType(Type t) {
            switch (t.getSort()) {
            case Type.ARRAY:
                addType(t.getElementType());
                break;

            case Type.OBJECT:
                addName(t.getClassName()
                        .replace('.', '/'));
                break;

            default:
            }
        }

        void addName(String name) {
            if (name == null) {
                return;
            }
            // decode arrays
            if (name.startsWith("[L") && name.endsWith(";")) {
                name = name.substring(2, name.length() - 1);
            }
            pkg.noteUse(name);
        }

        void addNames(String[] names) {
            if (names == null) {
                return;
            }
            for (String name : names) {
                addName(name);
            }
        }

        void addDesc(String desc) {
            addType(Type.getType(desc));
        }

        void addMethodDesc(String desc) {
            addType(Type.getReturnType(desc));

            Type[] types = Type.getArgumentTypes(desc);

            for (Type type : types) {
                addType(type);
            }
        }

        void analyze(InputStream in) throws IOException {
            ClassReader reader = new ClassReader(in);
            AnnotationVisitor annotationVisitor = new AnnotationVisitor(Opcodes.ASM5) {
                @Override
                public void visit(final String name, final Object value) {
                    if (value instanceof Type) {
                        addType((Type) value);
                    }
                }

                @Override
                public void visitEnum(final String name, final String desc, final String value) {
                    addDesc(desc);
                }

                @Override
                public AnnotationVisitor visitAnnotation(final String name, final String desc) {
                    addDesc(desc);

                    return this;
                }

                @Override
                public AnnotationVisitor visitArray(final String name) {
                    return this;
                }
            };
            SignatureVisitor signatureVisitor = new SignatureVisitor(Opcodes.ASM5) {
                @Override
                public void visitClassType(final String name) {
                    addName(name);
                }
            };
            FieldVisitor fieldVisitor = new FieldVisitor(Opcodes.ASM5) {

                @Override
                public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                    addDesc(desc);
                    return annotationVisitor;
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc,
                        boolean visible) {
                    addDesc(desc);
                    return annotationVisitor;
                }
            };

            MethodVisitor mv = new MethodVisitor(Opcodes.ASM5) {
                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    addDesc(desc);

                    return annotationVisitor;
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(final int parameter, final String desc,
                        final boolean visible) {
                    addDesc(desc);

                    return annotationVisitor;
                }

                @Override
                public void visitTypeInsn(final int opcode, final String desc) {
                    if (desc.charAt(0) == '[') {
                        addDesc(desc);
                    } else {
                        addName(desc);
                    }
                }

                @Override
                public void visitFieldInsn(final int opcode, final String owner, final String name,
                        final String desc) {
                    if (owner.charAt(0) != '[') {
                        addName(owner);
                    }
                }

                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                    if (owner.charAt(0) != '[') {
                        addName(owner);
                    }
                }

                @Override
                public void visitLdcInsn(final Object cst) {
                    if (cst instanceof Type) {
                        addType((Type) cst);
                    }
                }

                @Override
                public void visitMultiANewArrayInsn(final String desc, final int dims) {
                    addDesc(desc);
                }

                @Override
                public void visitTryCatchBlock(final Label start, final Label end, final Label handler,
                        final String type) {
                    addName(type);
                }

                @Override
                public void visitLocalVariable(final String name, final String desc, final String signature,
                        final Label start, final Label end, final int index) {
                    if (signature == null) {
                        addDesc(desc);
                    } else {
                        new SignatureReader(signature).acceptType(signatureVisitor);
                    }
                }

            };
            ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM5) {
                @Override
                public void visit(final int version, final int access, final String name, final String signature,
                        final String superName, final String[] interfaces) {
                    if (signature == null) {
                        addName(superName);
                        addNames(interfaces);
                    } else {
                        addSignature(signature);
                    }
                }

                @Override
                public AnnotationVisitor visitAnnotation(final String desc, final boolean visible) {
                    addDesc(desc);

                    return annotationVisitor;
                }

                @Override
                public FieldVisitor visitField(final int access, final String name, final String desc,
                        final String signature, final Object value) {
                    if (signature == null) {
                        addDesc(desc);
                    } else {
                        addTypeSignature(signature);
                    }

                    if (value instanceof Type) {
                        addType((Type) value);
                    }

                    return fieldVisitor;
                }

                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String desc,
                        final String signature, final String[] exceptions) {
                    if (signature == null) {
                        addMethodDesc(desc);
                    } else {
                        addSignature(signature);
                    }

                    if (exceptions != null) {
                        addNames(exceptions);
                    }

                    return mv;
                }

                void addSignature(String signature) {
                    if (signature != null) {
                        new SignatureReader(signature).accept(signatureVisitor);
                    }
                }

                void addTypeSignature(String signature) {
                    if (signature != null) {
                        new SignatureReader(signature).acceptType(signatureVisitor);
                    }
                }

            };

            reader.accept(classVisitor, 0);

        }

        class ResultCollector {

            private final Set<String> classes = new HashSet<String>();

            public Set<String> getDependencies() {
                return classes;
            }

            public void addName(String name) {
                if (name == null) {
                    return;
                }

                // decode arrays
                if (name.startsWith("[L") && name.endsWith(";")) {
                    name = name.substring(2, name.length() - 1);
                }

                // decode internal representation
                name = name.replace('/', '.');

                classes.add(name);
            }

            void addDesc(final String desc) {
                addType(Type.getType(desc));
            }

            void addType(final Type t) {
                switch (t.getSort()) {
                case Type.ARRAY:
                    addType(t.getElementType());
                    break;

                case Type.OBJECT:
                    addName(t.getClassName()
                            .replace('.', '/'));
                    break;

                default:
                }
            }

            public void add(String name) {
                classes.add(name);
            }

            void addNames(final String[] names) {
                if (names == null) {
                    return;
                }

                for (String name : names) {
                    addName(name);
                }
            }

            void addMethodDesc(final String desc) {
                addType(Type.getReturnType(desc));

                Type[] types = Type.getArgumentTypes(desc);

                for (Type type : types) {
                    addType(type);
                }
            }
        }
    }
}