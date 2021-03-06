/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package net.java.btrace.instr;

import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.List;
import net.java.btrace.annotations.Duration;
import net.java.btrace.annotations.Kind;
import net.java.btrace.annotations.ProbeClassName;
import net.java.btrace.annotations.ProbeMethodName;
import net.java.btrace.annotations.Return;
import net.java.btrace.annotations.Self;
import net.java.btrace.annotations.TargetInstance;
import net.java.btrace.annotations.TargetMethodOrField;
import net.java.btrace.annotations.Where;
import static net.java.btrace.org.objectweb.asm.Opcodes.*;
import static net.java.btrace.instr.Constants.*;
import net.java.btrace.api.extensions.ExtensionsRepository;
import net.java.btrace.api.extensions.ExtensionsRepositoryFactory;
import net.java.btrace.util.Messages;
import net.java.btrace.org.objectweb.asm.AnnotationVisitor;
import net.java.btrace.org.objectweb.asm.ClassReader;
import net.java.btrace.org.objectweb.asm.ClassVisitor;
import net.java.btrace.org.objectweb.asm.FieldVisitor;
import net.java.btrace.org.objectweb.asm.MethodVisitor;
import net.java.btrace.org.objectweb.asm.Type;
import net.java.btrace.org.objectweb.asm.Opcodes;
import net.java.btrace.api.extensions.util.CallTargetValidator;

/**
 * This class verifies that a BTrace program is safe
 * and well-formed.
 * Also it fills the onMethods and onProbes structures with the data taken from
 * the annotations
 *
 * @author A. Sundararajan
 * @autohr J. Bachorik
 */
public class Verifier extends ClassVisitor {
    public static final String BTRACE_SELF_DESC = Type.getDescriptor(Self.class);
    public static final String BTRACE_RETURN_DESC = Type.getDescriptor(Return.class);
    public static final String BTRACE_TARGETMETHOD_DESC = Type.getDescriptor(TargetMethodOrField.class);
    public static final String BTRACE_TARGETINSTANCE_DESC = Type.getDescriptor(TargetInstance.class);
    public static final String BTRACE_DURATION_DESC = Type.getDescriptor(Duration.class);
    public static final String BTRACE_PROBECLASSNAME_DESC = Type.getDescriptor(ProbeClassName.class);
    public static final String BTRACE_PROBEMETHODNAME_DESC = Type.getDescriptor(ProbeMethodName.class);

    private boolean seenBTrace;
    private String className;
    private List<OnMethod> onMethods;
    private List<OnProbe> onProbes;
    private boolean unsafe;
    private CycleDetector cycleDetector;
    private CallTargetValidator ctValidator;

    public Verifier(ClassVisitor cv, boolean unsafe, ExtensionsRepository eLocator) {
        super(Opcodes.ASM4, cv);
        this.unsafe = unsafe;
        onMethods = new ArrayList<OnMethod>();
        onProbes = new ArrayList<OnProbe>();
        cycleDetector = new CycleDetector();
        ctValidator = new CallTargetValidator(eLocator);
    }

    public Verifier(ClassVisitor cv) {
        this(cv, false, ExtensionsRepositoryFactory.builtin(ExtensionsRepository.Location.SERVER));
    }

    public String getClassName() {
        return className;
    }

    public List<OnMethod> getOnMethods() {
        return onMethods;
    }

    public List<OnProbe> getOnProbes() {
        return onProbes;
    }
    
    CallTargetValidator getCallTargetValidator() {
        return ctValidator;
    }

    @Override
    public void visitEnd() {
        if (cycleDetector.hasCycle()) {
            reportError("execution.loop.danger");
        }
        super.visitEnd();
    }

    public void visit(int version, int access, String name, 
            String signature, String superName, String[] interfaces) {
        if ((access & ACC_INTERFACE) != 0 ||
            (access & ACC_ENUM) != 0  ) {
            reportError("btrace.program.should.be.class");
        }
        if ((access & ACC_PUBLIC) == 0) {
            reportError("class.should.be.public", name);
        }

        if (! superName.equals(JAVA_LANG_OBJECT)) {
            reportError("object.superclass.required", superName);
        }
        if (interfaces != null && interfaces.length > 0) {
            reportError("no.interface.implementation");
        }
        className = name;
        super.visit(version, access, name, signature, 
                    superName, interfaces);
    }

    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
        if (desc.equals(BTRACE_DESC)) {
            seenBTrace = true;
        }
        return super.visitAnnotation(desc, visible);
    }

    public FieldVisitor	visitField(int access, String name, 
            String desc, String signature, Object value) {
        if (! seenBTrace) {
            reportError("not.a.btrace.program");
        }
        if ((access & ACC_STATIC) == 0) {
            reportError("agent.no.instance.variables", name);
        }
        return super.visitField(access, name, desc, signature, value);
    }
     
    public void visitInnerClass(String name, String outerName, 
            String innerName, int access) {
        if (className.equals(outerName)) {
            reportError("no.nested.class");
        }
    }
     
    public MethodVisitor visitMethod(final int access, final String methodName,
            final String methodDesc, String signature, String[] exceptions) {

        if (! seenBTrace) {
            reportError("not.a.btrace.program");
        }

        if ((access & ACC_SYNCHRONIZED) != 0) {
            reportError("no.synchronized.methods", methodName + methodDesc);
        }

        if (! methodName.equals(CONSTRUCTOR)) {
            if ((access & ACC_STATIC) == 0) {
                reportError("no.instance.method", methodName + methodDesc);
            }
        }

        MethodVisitor mv = super.visitMethod(access, methodName,
                   methodDesc, signature, exceptions);

        return new MethodVerifier(this, mv, className, cycleDetector, methodName + methodDesc) {
            private OnMethod om = null;
            private boolean asBTrace = false;

            @Override
            public void visitEnd() {
                if ((access & ACC_PUBLIC) == 0 && !methodName.equals(CLASS_INITIALIZER)) {
                    if (asBTrace) { // only btrace handlers are enforced to be public
                        reportError("method.should.be.public", methodName + methodDesc);
                    }
                }
                if (Type.getReturnType(methodDesc) != Type.VOID_TYPE) {
                    if (asBTrace) {
                        reportError("return.type.should.be.void", methodName + methodDesc);
                    }
                }
                super.visitEnd();
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, final String desc, boolean visible) {
                if (desc.equals(BTRACE_SELF_DESC)) {
                    // all allowed
                    if (om != null) {
                        om.setSelfParameter(parameter);
                    }
                }
                if (desc.equals(BTRACE_RETURN_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.RETURN ||
                            (om.getLocation().getValue() == Kind.CALL && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.ARRAY_GET && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.FIELD_GET && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.NEW && om.getLocation().getWhere() == Where.AFTER) ||
                            (om.getLocation().getValue() == Kind.NEWARRAY && om.getLocation().getWhere() == Where.AFTER)) {
                            om.setReturnParameter(parameter);
                        } else {
                            reportError("return.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_TARGETMETHOD_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.CALL ||
                            om.getLocation().getValue() == Kind.FIELD_GET ||
                            om.getLocation().getValue() == Kind.FIELD_SET) {
                            om.setTargetMethodOrFieldParameter(parameter);
                        } else {
                            reportError("called-method.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_TARGETINSTANCE_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.CALL ||
                            om.getLocation().getValue() == Kind.FIELD_GET ||
                            om.getLocation().getValue() == Kind.FIELD_SET) {
                            om.setTargetInstanceParameter(parameter);
                        } else {
                            reportError("called-instance.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_DURATION_DESC)) {
                    if (om != null) {
                        if (om.getLocation().getValue() == Kind.RETURN || om.getLocation().getValue() == Kind.ERROR) {
                            om.setDurationParameter(parameter);
                        } else {
                            reportError("duration.desc.invalid", methodName + methodDesc + "(" + parameter + ")");
                        }
                    }
                }
                if (desc.equals(BTRACE_PROBECLASSNAME_DESC)) {
                    // allowed for all
                    if (om != null) {
                        om.setClassNameParameter(parameter);
                    }
                }
                if (desc.equals(BTRACE_PROBEMETHODNAME_DESC)) {
                    // allowed for all
                    if (om != null) {
                        om.setMethodParameter(parameter);
                    }
                }
                final AnnotationVisitor superVisitor = super.visitParameterAnnotation(parameter, desc, visible);
                return new AnnotationVisitor(Opcodes.ASM4) {

                    public void visit(String string, Object o) {
                        if (om != null && string.equals("fqn")) { // NOI18N
                            if (desc.equals(BTRACE_TARGETMETHOD_DESC)) {
                                om.setTargetMethodOrFieldFqn((Boolean)o);
                            } else if (desc.equals(BTRACE_PROBEMETHODNAME_DESC)) {
                                om.setMethodFqn((Boolean)o);
                            }
                        }
                        if (superVisitor != null) superVisitor.visit(string, o);
                    }

                    public void visitEnum(String string, String string1, String string2) {
                        if (superVisitor != null) superVisitor.visitEnum(string, string1, string2);
                    }

                    public AnnotationVisitor visitAnnotation(String string, String string1) {
                        return superVisitor != null ? superVisitor.visitAnnotation(string, string1) : null;
                    }

                    public AnnotationVisitor visitArray(String string) {
                        return superVisitor != null ? superVisitor.visitArray(string) : null;
                    }

                    public void visitEnd() {
                        if (superVisitor != null) superVisitor.visitEnd();
                    }
                };
            }

            public AnnotationVisitor visitAnnotation(String desc,
                                  boolean visible) {
                if (desc.startsWith("Lnet/java/btrace/annotations/")) {
                    asBTrace = true;
                    cycleDetector.addStarting(new CycleDetector.Node(methodName + methodDesc));
                }

                if (desc.equals(ONMETHOD_DESC)) {
                    om = new OnMethod();
                    onMethods.add(om);
                    om.setTargetName(methodName);
                    om.setTargetDescriptor(methodDesc);
                    return new AnnotationVisitor(Opcodes.ASM4) {
                        public void	visit(String name, Object value) {
                            if (name.equals("clazz")) {
                                om.setClazz((String)value);
                            } else if (name.equals("method")) {
                                om.setMethod((String)value);
                            } else if (name.equals("type")) {
                                om.setType((String)value);
                            }
                        }

                        public AnnotationVisitor visitAnnotation(String name,
                                  String desc) {
                            if (desc.equals(LOCATION_DESC)) {
                                final Location loc = new Location();
                                return new AnnotationVisitor(Opcodes.ASM4) {
                                    public void visitEnum(String name, String desc, String value) {
                                        if (desc.equals(WHERE_DESC)) {
                                            loc.setWhere(Enum.valueOf(Where.class, value));
                                        } else if (desc.equals(KIND_DESC)) {
                                            loc.setValue(Enum.valueOf(Kind.class, value));
                                        }
                                    }

                                    public void	visit(String name, Object value) {
                                        if (name.equals("clazz")) {
                                            loc.setClazz((String)value);
                                        } else if (name.equals("method")) {
                                            loc.setMethod((String)value);
                                        } else if (name.equals("type")) {
                                            loc.setType((String)value);
                                        } else if (name.equals("field")) {
                                            loc.setField((String)value);
                                        } else if (name.equals("line")) {
                                            loc.setLine(((Number)value).intValue());
                                        }
                                    }

                                    public void visitEnd() {
                                        om.setLocation(loc);
                                    }
                                };
                            }

                            return super.visitAnnotation(name, desc);
                        }
                    };
                } else if (desc.equals(ONPROBE_DESC)) {
                    final OnProbe op = new OnProbe();
                    onProbes.add(op);
                    op.setTargetName(methodName);
                    op.setTargetDescriptor(methodDesc);
                    return new AnnotationVisitor(Opcodes.ASM4) {
                        public void visit(String name, Object value) {
                            if (name.equals("namespace")) {
                                op.setNamespace((String)value);
                            } else if (name.equals("name")) {
                                op.setName((String)value);
                            }
                        }
                    };
                } else {
                    return null;
                }
            }
        };
    }
 
    public void visitOuterClass(String owner, String name, 
            String desc) {
        reportError("no.outer.class");
    }

    void reportError(String err) {
        reportError(err, null);
    }

    void reportError(String err, String msg) {
        if (unsafe) return;
        String str = Messages.get(err);
        if (msg != null) {
            str += ": " + msg;
        }
        throw new VerifierException(str);
    }

    private static void usage(String msg) {
        System.err.println(msg);
        System.exit(1);
    }

    // simple test main
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage("java net.java.btrace.runtime.Verifier <.class file>");
        }

        args[0] = args[0].replace('.', '/');
        File file = new File(args[0] + ".class");
        if (! file.exists()) {
            usage("file '" + args[0] + ".class' does not exist");
        }
        FileInputStream fis = new FileInputStream(file);
        ClassReader reader = new ClassReader(new BufferedInputStream(fis));
        Verifier verifier = new Verifier(new ClassVisitor(Opcodes.ASM4){});
        reader.accept(verifier, 0);
    }
}
