package net.neoforged.neoforgegradle.dsl;

import net.neoforged.neoforgegradle.internal.utils.StringUtils;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

public abstract class Run implements Named {
    private final String name;
    /**
     * Sanitized name: converted to upper camel case and with invalid characters removed.
     */
    private final String baseName;

    @Inject
    public Run(String name, Project project) {
        this.name = name;
        this.baseName = StringUtils.toCamelCase(name, false);
        getMods().convention(project.getExtensions().getByType(NeoForgeExtension.class).getMods());
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract SetProperty<Mod> getMods();

    public abstract Property<String> getType();

    public void client() {
        getType().set("client");
    }

    public void data() {
        getType().set("data");
    }

    public void server() {
        getType().set("server");
    }

    // TODO: Move out of DSL class
    @ApiStatus.Internal
    public String getBaseName() {
        return baseName;
    }

    // TODO: Move out of DSL class
    @ApiStatus.Internal
    public String nameOf(@Nullable String prefix, @Nullable String suffix) {
        return StringUtils.uncapitalize((prefix == null ? "" : prefix) + this.baseName + (suffix == null ? "" : StringUtils.capitalize(suffix)));
    }
}