package com.microsoft.terraform;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class TerraformClient implements AutoCloseable {
    private static class OutputVariable {
        @JsonProperty("type") public String type;
        @JsonProperty("value") public Object value;
        @JsonProperty("sensitive") public boolean sensitive;
    }

    private static final String TERRAFORM_EXE_NAME = "terraform";
    private static final String VERSION_COMMAND = "version", INIT_COMMAND = "init", PLAN_COMMAND = "plan", APPLY_COMMAND = "apply", DESTROY_COMMAND = "destroy", OUTPUT_COMMAND = "output";
    private static final String USER_AGENT_ENV_NAME = "AZURE_HTTP_USER_AGENT", USER_AGENT_ENV_VALUE = "Java-TerraformClient", USER_AGENT_DELIMITER = ";";
    private static final Map<String, String> NON_INTERACTIVE_COMMAND_MAP = new HashMap<>();
    static {
        NON_INTERACTIVE_COMMAND_MAP.put(APPLY_COMMAND, "-auto-approve");
        NON_INTERACTIVE_COMMAND_MAP.put(DESTROY_COMMAND, "-force");
        NON_INTERACTIVE_COMMAND_MAP.put(OUTPUT_COMMAND, "-json");
    }

    private final ExecutorService executor = Executors.newWorkStealingPool();
    private final TerraformOptions options;
    private File workingDirectory;
    private boolean inheritIO;
    private Consumer<String> outputListener, errorListener;

    public TerraformClient() {
        this(new TerraformOptions());
    }

    public TerraformClient(TerraformOptions options) {
        assert options != null;
        this.options = options;
    }

    public Consumer<String> getOutputListener() {
        return this.outputListener;
    }

    public void setOutputListener(Consumer<String> listener) {
        this.outputListener = listener;
    }

    public Consumer<String> getErrorListener() {
        return this.errorListener;
    }

    public void setErrorListener(Consumer<String> listener) {
        this.errorListener = listener;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(File workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public void setWorkingDirectory(Path folderPath) {
        this.setWorkingDirectory(folderPath.toFile());
    }

    public boolean isInheritIO() {
        return this.inheritIO;
    }

    public void setInheritIO(boolean inheritIO) {
        this.inheritIO = inheritIO;
    }

    public CompletableFuture<String> version() throws IOException {
        ProcessLauncher launcher = this.getTerraformLauncher(VERSION_COMMAND);
        StringBuilder version = new StringBuilder();
        Consumer<String> outputListener = this.getOutputListener();
        launcher.setOutputListener(m -> {
            version.append(version.length() == 0 ? m : "");
            if (outputListener != null) {
                outputListener.accept(m);
            }
        });
        return launcher.launch().thenApply((c) -> c == 0 ? version.toString() : null);
    }

    public CompletableFuture<Boolean> plan() throws IOException {
        this.checkRunningParameters();
        return this.run(INIT_COMMAND, PLAN_COMMAND);
    }

    public CompletableFuture<Boolean> apply() throws IOException {
        this.checkRunningParameters();
        return this.run(INIT_COMMAND, APPLY_COMMAND);
    }

    public CompletableFuture<Boolean> destroy() throws IOException {
        this.checkRunningParameters();
        return this.run(INIT_COMMAND, DESTROY_COMMAND);
    }

    public CompletableFuture<Map<String, Object>> output() throws IOException {
        ProcessLauncher launcher = this.getTerraformLauncher(OUTPUT_COMMAND);
        StringBuilder output = new StringBuilder();
        Consumer<String> outputListener = this.getOutputListener();
        launcher.setOutputListener(m -> {
            output.append(m);
            if (outputListener != null) {
                outputListener.accept(m);
            }
        });

        return launcher.launch().thenApply((c) -> {
            if (c != 0) {
                return Collections.emptyMap();
            }
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                Map<String,Object> ret = new HashMap<>();
                Map<String,OutputVariable> rawMap = objectMapper.readValue(output.toString(), new TypeReference<Map<String,OutputVariable>>(){});
                for (Map.Entry<String, OutputVariable> entry: rawMap.entrySet()) {
                    ret.put(entry.getKey(), entry.getValue().value);
                }
                return ret;
            } catch (IOException e) {
                if (errorListener != null) {
                    errorListener.accept("JSON parse error: " + e.getMessage());
                }
                return Collections.emptyMap();
            }
        });

    }

    private CompletableFuture<Boolean> run(String... commands) throws IOException {
        assert commands.length > 0;
        ProcessLauncher[] launchers = new ProcessLauncher[commands.length];
        for (int i = 0; i < commands.length; i++) {
            launchers[i] = this.getTerraformLauncher(commands[i]);
        }

        CompletableFuture<Integer> result = launchers[0].launch().thenApply(c -> c == 0 ? 1 : -1);
        for (int i = 1; i < commands.length; i++) {
            result = result.thenCompose(index -> {
                if (index > 0) {
                    return launchers[index].launch().thenApply(c -> c == 0 ? index + 1 : -1);
                }
                return CompletableFuture.completedFuture(-1);
            });
        }
        return result.thenApply(i -> i > 0);
    }

    private void checkRunningParameters() {
        if (this.getWorkingDirectory() == null) {
            throw new IllegalArgumentException("working directory should not be null");
        }
    }

    private ProcessLauncher getTerraformLauncher(String command) throws IOException {
        ProcessLauncher launcher = new ProcessLauncher(this.executor, TERRAFORM_EXE_NAME, command);
        launcher.setDirectory(this.getWorkingDirectory());
        launcher.setInheritIO(this.isInheritIO());
        launcher.setOrAppendEnvironmentVariable(USER_AGENT_ENV_NAME, USER_AGENT_ENV_VALUE, USER_AGENT_DELIMITER);
        for (Map.Entry<String, String> e: this.options.getEnvVars().entrySet()) {
            launcher.setEnvironmentVariable(e.getKey(), e.getValue());
        }
        launcher.appendCommands(NON_INTERACTIVE_COMMAND_MAP.get(command));
        launcher.setOutputListener(this.getOutputListener());
        launcher.setErrorListener(this.getErrorListener());
        return launcher;
    }

    @Override
    public void close() throws Exception {
        this.executor.shutdownNow();
        if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("executor did not terminate");
        }
    }
}
