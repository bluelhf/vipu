package blue.lhf.vipu;

import blue.lhf.vipu.black_magic.*;
import blue.lhf.vipu.escaping.*;
import io.github.classgraph.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

public class Vipu extends JavaPlugin {
    private InjectingLibraryLoader libraryLoader;
    private Surma surma;

    @Override
    public void onEnable() {
        this.surma = new Surma((URLClassLoader) getServer().getClass().getClassLoader());
        this.libraryLoader = new InjectingLibraryLoader(surma,
            getDataFolder().toPath().resolve("repository"), getLogger());

        enablePlugins();
    }

    public boolean hasLoadedBefore() {
        try {
            getServer().getClass().getClassLoader().loadClass(VipuPlugin.class.getName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void enablePlugins() {
        if (hasLoadedBefore()) {
            getLogger().severe("Not loading plugins because they are already loaded.");
            getLogger().severe("Attempting to re-load plugins does not have any effect");
            getLogger().severe("because the JVM keeps the bytecode of the plugin classes");
            getLogger().severe("in memory. Restart the server to reload Vipu plugins.");

            return;
        }

        try {
            injectEscapingClasses();

            final Set<URI> pluginJars = getPluginJars();
            final Set<URI> injected = injectPluginJars(pluginJars);

            final ClassInfoList pluginClassInfos = scanPluginJars(injected);
            final Set<? extends Class<?>> pluginClasses = preloadPluginClasses(pluginClassInfos);
            final Set<? extends Class<?>> successfullyInjected = injectDependencies(pluginClasses);
            final int loaded = enablePlugins(successfullyInjected).size();

            getLogger().log(Level.INFO, "Loaded {0} plugins", loaded);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable plugins", e);
        }
    }

    private Set<? extends Class<?>> injectDependencies(final Collection<? extends Class<?>> pluginClasses) {
        final Set<Class<?>> successful = new HashSet<>();
        for (final Class<?> pluginClass : pluginClasses) {
            try {
                libraryLoader.injectDependencies(pluginClass);
                successful.add(pluginClass);
            } catch (Exception e) {
                getLogger().warning("Failed to load dependencies for " + pluginClass.getName());
            }
        }

        return successful;
    }

    private Set<URI> getPluginJars() throws IOException {
        final Path dataFolder = getDataFolder().toPath();
        if (!Files.exists(dataFolder) || !Files.isDirectory(dataFolder)) return Set.of();
        try (final Stream<Path> stream = Files.list(dataFolder).filter(Files::isRegularFile)) {
            return stream.map(Path::toUri).collect(toSet());
        }
    }

    /**
     * Injects the classes that should escape the plugin class loader into the server class loader.
     * */
    private void injectEscapingClasses() throws Exception {
        surma.injectSingle(Name.class);
        surma.injectSingle(Libraries.class);
        surma.injectSingle(VipuPlugin.class);
    }

    /**
     * Injects the given JAR files into the server class loader.
     * @param plugins The URIs of the JAR files to inject.
     * @return The URIs of the JAR files that were successfully injected.
     * */
    private Set<URI> injectPluginJars(final Set<URI> plugins) {
        final Set<URI> injected = new HashSet<>();
        for (final URI uri : plugins) {
            try {
                surma.injectJAR(uri.toURL());
                injected.add(uri);
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Failed to inject " + uri, e);
            }
        }

        return injected;
    }


    /**
     * Scans the given JAR files for classes that implement {@link VipuPlugin}.
     * @param uris The URIs of the JAR files to scan.
     * @return The classes that implement {@link VipuPlugin}, as a {@link ClassInfoList}.
     * */
    private ClassInfoList scanPluginJars(final Set<URI> uris) {
        final ClassGraph classGraph = new ClassGraph()
            .enableAnnotationInfo()
            .enableClassInfo();

        for (final URI uri : uris) {
            classGraph.overrideClasspath(uri);
        }

        try (final ScanResult result = classGraph.scan()) {
            return result.getClassesImplementing(VipuPlugin.class);
        }
    }

    /**
     * Loads the given classes without initialising them.
     * @param plugins The classes to load.
     * @return The classes that were successfully loaded.
     * */
    private Set<? extends Class<?>> preloadPluginClasses(final ClassInfoList plugins) {
        return Functional.map(plugins.stream(), this::preloadedClass, (plugin, e) ->
            getLogger().warning("Failed to load class " + plugin.getName())).collect(toSet());
    }

    /**
     * Constructs and enables the given plugins from their classes.
     * @param pluginClasses The classes of the plugins to enable.
     * @return The classes of the plugins that were successfully enabled.
     * */
    private Set<Class<?>> enablePlugins(final Set<? extends Class<?>> pluginClasses) {
        return Functional.map(pluginClasses.stream(), this::enablePlugin, (plugin, e) ->
            getLogger().warning("Failed to enable " + plugin.getName())).collect(toSet());
    }

    /**
     * Constructs and enables the given plugin from its class.
     * @param pluginClass The class of the plugin to enable.
     * @return The class of the plugin that was enabled.
     * */
    private Class<?> enablePlugin(final Class<?> pluginClass) throws Exception {
        return initialize(pluginClass);
    }

    @NotNull
    private Class<?> preloadedClass(final ClassInfo info) throws ClassNotFoundException {
        return Class.forName(info.getName(), false, surma.getTarget());
    }

    @NotNull
    private Class<?> initialize(final Class<?> clazz) throws ClassNotFoundException {
        return Class.forName(clazz.getName(), true, clazz.getClassLoader());
    }
}