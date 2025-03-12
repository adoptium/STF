/*******************************************************************************
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*******************************************************************************/

package net.adoptopenjdk.blockedexitagent;

import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.LocalVariablesSorter;

import static org.objectweb.asm.Opcodes.*;

/*
 * Replace all calls to System.exit for load tests with BlockedExitException.
 * This ensures the test framework will not shut down before all tests
 * have completed.
 */
class BlockedExitAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new BlockedExitTransformer(), true);
    }
    static class BlockedExitTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classBytes) {
            /* System.exit calls in LoadTest class should not be overwritten. */
            if ((null != loader) && (!className.contains("net/adoptopenjdk/loadTest/LoadTest"))) {    
                ClassReader cr = new ClassReader(classBytes);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                cr.accept(new BlockedExitClassVisitor(cw), ClassReader.EXPAND_FRAMES);
                return cw.toByteArray();
            } else {
                return null;
            }
        }
    }

    public static class BlockedExitClassVisitor extends ClassVisitor {
        public BlockedExitClassVisitor(ClassVisitor cv) {
            super(ASM9, cv);
        }

        @Override
        public MethodVisitor visitMethod(int methodAccess, String methodName, String methodDesc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(methodAccess, methodName, methodDesc, signature, exceptions);
            return new BlockedExitMethodVisitor(methodAccess, methodDesc, methodVisitor);
        }
    }

    static class BlockedExitMethodVisitor extends LocalVariablesSorter {
        public BlockedExitMethodVisitor(int access, String descriptor, MethodVisitor methodVisitor) {
            super(ASM9, access, descriptor, methodVisitor);
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (isSystemExitInsn(opcode, owner, name, descriptor)) {
                String blockedExitException = "net/adoptopenjdk/loadTest/BlockedExitException";
                /* The bytecode just before this will have loaded the exit code
                 * onto the stack. Store it in a new local variable so it can
                 * be passed into the new exception.
                 */
                int localId = super.newLocal(Type.INT_TYPE);

                super.visitVarInsn(ISTORE, localId);
                super.visitTypeInsn(NEW, blockedExitException);
                super.visitInsn(DUP);
                super.visitVarInsn(ILOAD, localId);
                super.visitMethodInsn(INVOKESPECIAL, blockedExitException, "<init>", "(I)V");
                super.visitInsn(ATHROW);
                /* Manually update the stack map frame instead of using COMPUTE_FRAMES which was
                 * causing a duplicate class loading error.
                 */
                super.visitFrame(F_APPEND, 3, new Object[] {INTEGER, INTEGER, "java/lang/Object"}, 0, null);
            } else {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }

        private boolean isSystemExitInsn(int opcode, String owner, String name, String descriptor) {
            return (opcode == INVOKESTATIC)
                && "java/lang/System".equals(owner)
                && "exit".equals(name)
                && "(I)V".equals(descriptor);
        }
    }
}
