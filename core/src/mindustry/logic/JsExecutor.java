package mindustry.logic;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import arc.*;
import arc.util.Time;
import arc.func.*;
import arc.util.*;

import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.world.meta.BlockFlag;

import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.WrapFactory;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.debug.DebugFrame;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.debug.Debugger;

public class JsExecutor extends LExecutor implements Debugger {
    public Context context;
    public Scriptable scope;
    public String code;
    public String securedCode;
    private boolean isInitialized;
    private boolean isRunning = false;
    private Thread executionThread;
    public final Object singleStepLock = new Object();
    public boolean stopExecution = false;
    private int currentLineNumber = 1;
    public String consoleLog = "";
    public Cons<String> consoleListener;
    public LAssembler builder = new LAssembler();
    public Console console;
    public JsWrapper mindustry;
    public long sleepUntil = 0;

    public JsExecutor() {
        this.isInitialized = false;
    }

    // Loads the JavaScript code into the executor
    @Override
    public void load(LAssembler builder) {
        builder.putVar("test");
        /*
         * vars = builder.vars.values().toSeq().retainAll(var ->
         * !var.constant).toArray(LVar.class);
         * for (int i = 0; i < vars.length; i++) {
         * vars[i].id = i;
         * }
         */

        counter = builder.getVar("@counter");
        unit = builder.getVar("@unit");
        thisv = builder.getVar("@this");
        ipt = builder.putConst("@ipt", build != null ? build.ipt : 0);
        this.builder = builder;
        code = builder.code;

        if (code.isEmpty()) {
            code = """
                    var duo = cpu.links().duo1;
                    var vault = cpu.links().vault1;
                    console.log("vault copper: " + vault.sensor("@copper"))
                    console.log("dou range: " + duo.sensor("@range"))
                    """;
        }
        this.isInitialized = !code.isEmpty();

        instructions = LAssembler.assemble("ubind", false).instructions;

        // stop execution thread, if already running
        if (executionThread != null) {
            while (isRunning) {
                stopExecution = true;
                synchronized (singleStepLock) {
                    singleStepLock.notify(); // Resume the paused thread
                }
            }
            cleanupContext();
        }

        if (isInitialized) {
            stopExecution = false;
            isRunning = true;
            executionThread = new Thread(() -> {
                initializeContext(); // Initialize the context and start the script
                try {
                    while (true)
                        context.evaluateString(scope, this.securedCode, "script", 1, null);
                } catch (Error e) {
                    if (consoleListener != null) {
                        if (e.getMessage() == "Script execution aborted") {
                            consoleListener.get("Code and state resetted");
                        } else {
                            consoleListener.get("Error from JS code." + getStackTrace(e));
                        }
                    }
                } finally {
                    cleanupContext(); // Ensure context cleanup on script completion
                    isRunning = false;
                }
            });
            executionThread.start();
        }
    }

    public void createSecuredCode(String code) {
        // prevent while(1); etc. from hanging
        securedCode = code.replaceAll("\\bwhile\\b(\\s*)\\(", "while$1(cpu.yield()||");

        // prevent for(var i = 0; i < 0; i++); etc. from hanging
        securedCode = code.replaceAll("\\bfor\\b(\\s*)\\(([^;]*;[^;]*;)", "for$1($2cpu.yield(),");

    }

    public static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // Executes exactly one line of code
    @Override
    public void runOnce() {
        if (!isInitialized || context == null) {
            return;
        }
        if (sleepUntil > Time.nanos()) {
            return;
        }
        synchronized (singleStepLock) {
            singleStepLock.notify(); // Resume the paused thread
        }
    }

    // will be called from the script thread eg. via cpu.yield()
    public void sendToYield() {
        if (stopExecution) {
            throw new Error("Script execution aborted");
        }
        synchronized (singleStepLock) {
            try {
                singleStepLock.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    // Returns if the executor is initialized with code
    @Override
    public boolean initialized() {
        return isInitialized;
    }

    public static class SandboxNativeJavaObject extends NativeJavaObject {
        public SandboxNativeJavaObject(Scriptable scope, Object javaObject, Class staticType) {
            super(scope, javaObject, staticType);
        }

        @Override
        public Object get(String name, Scriptable start) {
            if (name.equals("getClass")) {
                return NOT_FOUND;
            }

            return super.get(name, start);
        }
    }

    public static class SandboxWrapFactory extends WrapFactory {
        @Override
        public Scriptable wrapAsJavaObject(Context cx, Scriptable scope, Object javaObject, Class staticType) {
            return new SandboxNativeJavaObject(scope, javaObject, staticType);
        }
    }

    public class SandboxContextFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            switch (featureIndex) {
                case Context.FEATURE_ENABLE_JAVA_MAP_ACCESS:
                    return true;
            }
            return super.hasFeature(cx, featureIndex);
        }

        @Override
        protected Context makeContext() {
            Context cx = super.makeContext();
            cx.setWrapFactory(new SandboxWrapFactory());
            cx.setClassShutter(new ClassShutter() {
                public boolean visibleToScripts(String className) {
                    if (className.startsWith("JsWrapper"))
                        return true;
                    if (className.startsWith("JsExecutor$"))
                        return true;
                    if (className.startsWith("java.lang.Object"))
                        return true;
                    if (className.startsWith("java.lang.Class"))
                        return true;
                    if (className.startsWith("java.lang.String"))
                        return true;
                    if (className.startsWith("java.lang.Object"))
                        return true;

                    return false;
                }
            });
            return cx;
        }
    }

    // Initializes the context and attaches the debugger
    private void initializeContext() {
        ContextFactory sandboxFactory = new ContextFactory();
        context = sandboxFactory.enterContext();
        // context = Context.enter();
        context.setOptimizationLevel(-1); // Run in interpreted mode for easier debugging
        context.setInstructionObserverThreshold(1);
        context.setGeneratingDebug(true);
        context.setDebugger(this, null);
        scope = context.initStandardObjects();

        createConsole();
    }

    private void createConsole() {
        console = new Console();
        mindustry = new JsWrapper(this, console, scope);
    }

    public void setConsoleListener(Cons<String> listener) {
        consoleListener = listener;
    }

    public String getConsoleLog() {
        return consoleLog;
    }

    @Override
    public void handleCompilationDone(Context cx, DebuggableScript fnOrScript, String source) {
        // No additional action needed here
    }

    @Override
    public DebugFrame getFrame(Context cx, DebuggableScript fnOrScript) {
        return new SandboxDebugFrame(this);
    }

    private class SandboxDebugFrame implements DebugFrame {

        private final JsExecutor executor;

        public SandboxDebugFrame(JsExecutor executor) {
            this.executor = executor;
        }

        @Override
        public void onEnter(Context cx, Scriptable activation, Scriptable thisObj, Object[] args) {
            // No additional action needed on function entry
        }

        @Override
        public void onLineChange(Context cx, int lineNumber) {
            executor.counter.numval = lineNumber;
            executor.currentLineNumber = lineNumber;
            if (executor.stopExecution) {
                throw new Error("Script execution aborted");
            }
            synchronized (executor.singleStepLock) {
                try {
                    executor.singleStepLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void onExit(Context cx, boolean byThrow, Object resultOrException) {
            // No additional action needed on function exit
        }

        @Override
        public void onExceptionThrown(Context cx, Throwable ex) {
            ex.printStackTrace();
            executor.cleanupContext();
        }

        @Override
        public void onDebuggerStatement(Context cx) {
            // Handle 'debugger' statements if present
        }
    }

    public int getCurrentLineNumber() {
        return currentLineNumber;
    }

    private void cleanupContext() {
        if (context != null) {
            Context.exit(); // Properly exit the context
            context = null; // Nullify the context to allow reinitialization
            scope = null; // Nullify the scope for a fresh start
        }
    }

    public class Console {
        private StringBuilder logContent;

        public Console() {
            this.logContent = new StringBuilder();
        }

        public void clear() {
            logContent.setLength(0);
            if (consoleListener != null)
                consoleListener.get(getLogContent());
        }

        public void log(String string) {
            appendMessage("LOG: ", string);
        }

        public void warn(String string) {
            appendMessage("WARN: ", string);
        }

        public void error(String string) {
            appendMessage("ERROR: ", string);
        }

        private void appendMessage(String prefix, String string) {
            logContent.append(prefix);
            logContent.append(string).append(" ");
            logContent.append("\n");
            if (consoleListener != null)
                consoleListener.get(getLogContent());
        }

        public String getLogContent() {
            return logContent.toString();
        }
    }

    public Set<String> getAllVariableNames() {
        Set<String> variables = new HashSet<>();
        if (scope == null)
            return variables;
        for (Object id : scope.getIds()) {
            variables.add(id.toString());
        }
        return variables;
    }

    public Object getVariableValue(String name) {
        if (scope == null)
            return null;
        Object value = scope.get(name, scope);
        return value == Scriptable.NOT_FOUND ? null : Context.jsToJava(value, Object.class);
    }

    public void setVariableValue(String name, Object value) {
        if (scope == null)
            return;
        scope.put(name, scope, value);
    }
}