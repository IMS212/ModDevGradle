package net.neoforged.moddevgradle.internal;

import net.neoforged.moddevgradle.internal.utils.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.event.Level;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes the necessary argument files for running JUnit tests via the fml-junit plugin.
 */
abstract class PrepareArgsForTesting extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getGameDirectory();

    @OutputFile
    public abstract RegularFileProperty getVmArgsFile();

    @OutputFile
    public abstract RegularFileProperty getProgramArgsFile();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getLog4jConfigFile();

    @Classpath
    public abstract ConfigurableFileCollection getNeoForgeModDevConfig();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getAssetProperties();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getLegacyClasspathFile();

    @Classpath
    @InputFiles
    abstract ConfigurableFileCollection getModules();

    @Input
    public abstract Property<Level> getGameLogLevel();

    @Inject
    public PrepareArgsForTesting() {
    }

    private List<String> getInterpolatedJvmArgs(UserDevRunType runConfig) {
        var result = new ArrayList<String>();
        for (String arg : runConfig.jvmArgs()) {
            if (arg.equals("{modules}")) {
                arg = getModules().getFiles().stream()
                        .map(File::getAbsolutePath)
                        .collect(Collectors.joining(File.pathSeparator));
            }
            result.add(RunUtils.escapeJvmArg(arg));
        }
        return result;
    }

    @TaskAction
    public void prepareRun() throws IOException {
        // Make sure the run directory exists
        // IntelliJ refuses to start a run configuration whose working directory does not exist
        var runDir = getGameDirectory().get().getAsFile();
        Files.createDirectories(runDir.toPath());

        var userDevConfig = UserDevConfig.from(getNeoForgeModDevConfig().getSingleFile());
        var runConfig = userDevConfig.runs().get("client");
        if (runConfig == null) {
            throw new GradleException("The unit testing plugin requires a 'client' run-type to be made available by NeoForge. Available run types: " + userDevConfig.runs().keySet());
        }

        writeJvmArguments(runConfig);
        writeProgramArguments(runConfig);
    }

    private void writeJvmArguments(UserDevRunType runConfig) throws IOException {
        var lines = new ArrayList<String>();

        lines.addAll(getInterpolatedJvmArgs(runConfig));

        // Write log4j2 configuration file
        if (getLog4jConfigFile().isPresent()) {
            var log4jConfigFile = getLog4jConfigFile().get().getAsFile();
            RunUtils.writeLog4j2Configuration(getGameLogLevel().get(), log4jConfigFile.toPath());
            lines.add(RunUtils.escapeJvmArg("-Dlog4j2.configurationFile=" + log4jConfigFile.getAbsolutePath()));
        }

        for (var prop : runConfig.props().entrySet()) {
            var propValue = prop.getValue();
            if (propValue.equals("{minecraft_classpath_file}")) {
                propValue = getLegacyClasspathFile().getAsFile().get().getAbsolutePath();
            }

            addSystemProp(prop.getKey(), propValue, lines);
        }

        FileUtils.writeLinesSafe(getVmArgsFile().get().getAsFile().toPath(), lines);
    }

    private void writeProgramArguments(UserDevRunType runConfig) throws IOException {
        var lines = new ArrayList<String>();

        var assetProperties = RunUtils.loadAssetProperties(getAssetProperties().get().getAsFile());
        List<String> args = runConfig.args();
        for (int i = 0; i < args.size(); i++) {
            var arg = args.get(i);
            switch (arg) {
                case "--target" -> {
                    i++; // Skip the next argument too

                    lines.add("--target");
                    lines.add("forgejunituserdev");
                }
                case "{assets_root}" -> arg = Objects.requireNonNull(assetProperties.assetsRoot(), "assets_root");
                case "{asset_index}" -> arg = Objects.requireNonNull(assetProperties.assetIndex(), "asset_index");
            }
            lines.add(RunUtils.escapeJvmArg(arg));
        }

        FileUtils.writeLinesSafe(getProgramArgsFile().get().getAsFile().toPath(), lines);
    }

    private static void addSystemProp(String name, String value, List<String> lines) {
        lines.add(RunUtils.escapeJvmArg("-D" + name + "=" + value));
    }
}
