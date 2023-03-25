package blue.lhf.vipu;

import blue.lhf.vipu.black_magic.*;
import blue.lhf.vipu.escaping.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class Vipu extends JavaPlugin {
    private ServiceLoader<?> serviceLoader;
    private InjectingLibraryLoader libraryLoader;
    private Surma surma;


    @Override
    public void onEnable() {
        this.surma = new Surma((URLClassLoader) getServer().getClass().getClassLoader());
        this.libraryLoader = new InjectingLibraryLoader(surma,
            getDataFolder().toPath().resolve("repository"), getLogger());

        if (hasLoadedBefore()) {
            getLogger().severe("Not loading plugins because they are already loaded.");
            getLogger().severe("Attempting to re-load plugins does not have any effect");
            getLogger().severe("because the JVM keeps the bytecode of the plugin classes");
            getLogger().severe("in memory. Restart the server to reload Vipu plugins.");
        } else {
            try {
                serviceLoader = injectPlugins();
                enablePlugins();
            } catch (Exception e) {
                getLogger().log(Level.SEVERE, "Failed to enable plugins", e);
            }
        }
    }

    @Override
    public void onDisable() {
        disablePlugins();
    }

    public boolean hasLoadedBefore() {
        try {
            getServer().getClass().getClassLoader().loadClass(VipuPlugin.class.getName());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public Stream<VipuPlugin> getPlugins() {
        if (serviceLoader == null) return Stream.of();
        return serviceLoader.stream().map(ServiceLoader.Provider::get).map(
            o -> surma.reflectiveProxy(VipuPlugin.class, o));
    }


    private ServiceLoader<?> injectPlugins() throws Exception {
        surma.injectSingle(Libraries.class);
        final Class<?> injectedPluginInterface = surma.injectSingle(VipuPlugin.class);

        final Path data = getDataFolder().toPath();
        if (Files.exists(data) && Files.isDirectory(data)) {
            try (final Stream<Path> stream = Files.list(data)) {
                final Iterator<Path> files = stream.iterator();
                while (files.hasNext()) {
                    final Path path = files.next();
                    if (!Files.isRegularFile(path)) continue;
                    try (final JarFile jar = new JarFile(path.toFile())) {
                        surma.injectJAR(path.toUri().toURL());
                    } catch (Exception e) {
                        getLogger().log(Level.WARNING, "Failed to load " + path.getFileName(), e);
                    }
                }
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Could not read plugins directory", e);
            }
        }

        return ServiceLoader.load(injectedPluginInterface, surma.getTarget());
    }

    private void enablePlugins() {
        getPlugins().forEach(this::enablePlugin);
    }

    private void enablePlugin(final VipuPlugin plugin) {
        try {
            libraryLoader.injectDependencies(plugin);
            plugin.onEnable();
        } catch (DependencyResolutionException | ClassNotFoundException e) {
            getLogger().log(Level.WARNING, "Could not load plugin %s".formatted(plugin.getName()), e);
        }
    }

    private void disablePlugins() {
        getPlugins().forEach(VipuPlugin::onDisable);
    }
}