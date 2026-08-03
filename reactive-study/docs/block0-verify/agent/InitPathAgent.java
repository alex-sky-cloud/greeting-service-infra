package block0verify;

import java.io.PrintWriter;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Logs method entry + stack trace for Block 0 init path verification.
 */
public final class InitPathAgent {

    private static PrintWriter log;

    private static final Set<String> TARGETS = Set.of(
            "org/springframework/boot/reactor/netty/NettyReactiveWebServerFactory",
            "org/springframework/boot/reactor/netty/NettyWebServer",
            "reactor/netty/http/server/HttpServerBind",
            "reactor/netty/transport/ServerTransport",
            "reactor/netty/transport/TransportConnector",
            "reactor/netty/transport/ServerTransportConfig",
            "reactor/netty/http/HttpResources",
            "reactor/netty/resources/DefaultLoopResources",
            "io/netty/bootstrap/ServerBootstrap",
            "io/netty/bootstrap/AbstractBootstrap",
            "io/netty/channel/nio/AbstractNioChannel",
            "reactor/core/publisher/Mono"
    );

    private static final Set<String> METHODS = Set.of(
            "getWebServer",
            "start",
            "startHttpServer",
            "bind",
            "bindNow",
            "eventLoopGroup",
            "childEventLoopGroup",
            "onServerSelect",
            "onServer",
            "cacheNioSelectLoops",
            "cacheNativeSelectLoops",
            "doBind",
            "doBeginRead",
            "addAndSubmit",
            "doInitAndRegister"
    );

    public static void premain(String agentArgs, Instrumentation instrumentation) throws Exception {
        Path logPath = Path.of(System.getProperty("block0.agent.log", "block0-init-trace.log"));
        log = new PrintWriter(Files.newBufferedWriter(logPath), true);
        log.println("InitPathAgent started, log=" + logPath.toAbsolutePath());
        instrumentation.addTransformer(new InitPathTransformer(), true);
    }

    public static void logEntry(String clazz, String method) {
        log.println(">>> ENTER " + clazz.replace('/', '.') + "#" + method);
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (ste.getClassName().startsWith("block0verify.")) {
                continue;
            }
            log.println("    at " + ste);
        }
        log.println();
    }

    static final class InitPathTransformer implements ClassFileTransformer {
        @Override
        public byte[] transform(Module module, ClassLoader loader, String className,
                                Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                                byte[] classfileBuffer) {
            if (className == null || !TARGETS.contains(className)) {
                return null;
            }
            try {
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                reader.accept(new InitPathClassVisitor(className, writer), ClassReader.EXPAND_FRAMES);
                return writer.toByteArray();
            } catch (Throwable t) {
                log.println("transform failed for " + className + ": " + t);
                return null;
            }
        }
    }

    static final class InitPathClassVisitor extends ClassVisitor {
        private final String className;

        InitPathClassVisitor(String className, ClassWriter writer) {
            super(Opcodes.ASM9, writer);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!METHODS.contains(name)) {
                return mv;
            }
            return new LoggingMethodVisitor(mv, className, name);
        }
    }

    static final class LoggingMethodVisitor extends MethodVisitor {
        private final String className;
        private final String methodName;

        LoggingMethodVisitor(MethodVisitor mv, String className, String methodName) {
            super(Opcodes.ASM9, mv);
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public void visitCode() {
            super.visitCode();
            mv.visitLdcInsn(className);
            mv.visitLdcInsn(methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                    Type.getInternalName(InitPathAgent.class),
                    "logEntry",
                    "(Ljava/lang/String;Ljava/lang/String;)V",
                    false);
        }
    }
}
