package mindustry.logic;

import java.io.*;
import arc.*;
import arc.func.*;
import arc.util.*;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.FunctionObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.debug.DebugFrame;
import org.mozilla.javascript.debug.DebuggableScript;
import org.mozilla.javascript.debug.Debugger;

public class JsExecutor extends LExecutor implements Debugger {
    private Context context;
    private Scriptable scope;
    public String code;
    private boolean isInitialized;
    private boolean isRunning = false;
    private Thread executionThread;
    public final Object singleStepLock = new Object();
    public boolean stopExecution = false;
    private int currentLineNumber = 1;
    public String consoleLog = "";
    public Cons<String> consoleListener;

    public JsExecutor() {
        this.isInitialized = false;
    }

    // Loads the JavaScript code into the executor
    @Override
    public void load(LAssembler builder) {
        builder.putVar("test");
        vars = builder.vars.values().toSeq().retainAll(var -> !var.constant).toArray(LVar.class);
        for (int i = 0; i < vars.length; i++) {
            vars[i].id = i;
        }

        counter = builder.getVar("@counter");
        load(builder.code);
    }

    public void load(String code) {
        if (code.isEmpty()) {
            code = """
                    var count = 0;
                    while (count < 10) {
                        count++;
                        console.log("count: " + count);
                    }
                    var test = 10;
                    while (test > 0){
                        test--;
                        console.log("test: " + test);
                    }
                    """;
        }
        this.code = code;
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
                        context.evaluateString(scope, this.code, "script", 1, null);
                } catch (Error e) {

                    consoleListener.get("Error from JS code." + getStackTrace(e));
                    /* nothing to do */
                } finally {
                    cleanupContext(); // Ensure context cleanup on script completion
                    isRunning = false;
                }
            });
            executionThread.start();
        }
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
        synchronized (singleStepLock) {
            singleStepLock.notify(); // Resume the paused thread
        }
    }

    // Returns if the executor is initialized with code
    @Override
    public boolean initialized() {
        return isInitialized;
    }

    // Initializes the context and attaches the debugger
    private void initializeContext() {
        context = Context.enter();
        context.setOptimizationLevel(-1); // Run in interpreted mode for easier debugging
        context.setInstructionObserverThreshold(1);
        context.setGeneratingDebug(true);
        context.setDebugger(this, null);
        scope = context.initStandardObjects();
        scope.put("executor", scope, this);

        createConsole();
    }

    private void createConsole() {
        Scriptable console = context.newObject(scope);
        try {
            FunctionObject logFunction = new FunctionObject(
                    "log",
                    JsExecutor.class.getMethod("log", Context.class, Scriptable.class, Object[].class,
                            Function.class),
                    scope);
            console.put("log", console, logFunction);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to define custom print function.", e);
        }
        scope.put("console", scope, console);
    }

    public static void log(Context cx, Scriptable thisObj, Object[] args, Function func) {
        JsExecutor executor = (JsExecutor) thisObj.get("executor", thisObj);
        for (Object arg : args) {
            executor.consoleLog += Context.toString(arg) + " ";
        }
        executor.consoleLog = executor.consoleLog.substring(Math.max(0, executor.consoleLog.length() - 500));
        if (executor.consoleListener != null) {
            executor.consoleListener.get(executor.consoleLog);
        }
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
        return new CustomDebugFrame(this);
    }

    private class CustomDebugFrame implements DebugFrame {

        private final JsExecutor executor;

        public CustomDebugFrame(JsExecutor executor) {
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
}