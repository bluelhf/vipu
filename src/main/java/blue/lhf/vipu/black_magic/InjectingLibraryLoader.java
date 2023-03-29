package blue.lhf.vipu.black_magic;

import blue.lhf.vipu.escaping.*;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.*;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.*;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transfer.*;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.annotation.Annotation;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.*;

import static blue.lhf.vipu.Functional.bind1;
import static org.apache.maven.repository.internal.MavenRepositorySystemUtils.*;
import static org.eclipse.aether.repository.RepositoryPolicy.CHECKSUM_POLICY_FAIL;

/**
 * Downloads the dependency libraries of a {@link VipuPlugin} from the <a href="https://repo.maven.apache.org/maven2/">Maven Central repository</a> and injects them into the classpath.
 * <p>
 *     The code to load the libraries is based on the code of the <a href="https://hub.spigotmc.org/stash/projects/SPIGOT/repos/bukkit/browse/src/main/java/org/bukkit/plugin/java/LibraryLoader.java">Bukkit library loader</a>.
 * </p>
 * @see org.bukkit.plugin.java.LibraryLoader
 * */
public class InjectingLibraryLoader {
    private final Logger logger;
    private final RepositorySystem repository;
    private final DefaultRepositorySystemSession session;
    private final List<RemoteRepository> repositories;
    private final Surma surma;

    public InjectingLibraryLoader(final Surma surma, final Path repositoryPath, @NotNull Logger logger) {
        this.surma = surma;
        this.logger = logger;

        final var locator = newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        this.repository = locator.getService(RepositorySystem.class);
        this.session = newSession();

        session.setChecksumPolicy(CHECKSUM_POLICY_FAIL);
        session.setLocalRepositoryManager(repository.newLocalRepositoryManager(session, new LocalRepository(repositoryPath.toFile())));
        session.setTransferListener(new AbstractTransferListener() {
            @Override
            public void transferStarted(@NotNull TransferEvent event) {
                logger.log(Level.INFO, "Downloading {0}",
                    event.getResource().getRepositoryUrl() + event.getResource().getResourceName());
            }
        });

        session.setReadOnly();

        this.repositories = repository.newResolutionRepositories( session,
            Collections.singletonList(new RemoteRepository.Builder(
                "central", "default", "https://repo.maven.apache.org/maven2"
            ).build()));
    }

    public void injectDependencies(final Class<?> plugin) throws Exception {
        final String name = getName(plugin);

        final Annotation[] annotations = plugin.getAnnotationsByType(surma.loadInjected(Libraries.class));
        final Function<Object, Libraries> libraryByProxy = bind1(surma::reflectiveProxy, Libraries.class);
        final String[] libraries = Arrays.stream(annotations)
                                         .map(libraryByProxy).map(Libraries::value)
                                         .flatMap(Arrays::stream).toArray(String[]::new);


        if (libraries.length == 0) return;
        logger.log(Level.INFO, "[{0}] Loading {1} libraries... please wait", new Object[] {
            name, libraries.length
        });

        final List<Dependency> dependencies = new ArrayList<>();
        for (final String library : libraries) {
            final Artifact artifact = new DefaultArtifact(library);
            final Dependency dependency = new Dependency(artifact, null);
            dependencies.add(dependency);
        }

        final DependencyResult result = repository.resolveDependencies(session, new DependencyRequest(
            new CollectRequest((Dependency) null, dependencies, repositories), null));

        for (final ArtifactResult artifact : result.getArtifactResults()) {
            final File file = artifact.getArtifact().getFile();

            try {
                surma.injectJAR(file.toURI().toURL());
            } catch (MalformedURLException ex) {
                throw new AssertionError("Path to file was not a valid URL?", ex);
            }

            logger.log(Level.INFO, "[{0}] Loaded library {1}", new Object[] {
                name, file
            });
        }
    }

    private String getName(final Class<?> plugin) throws ClassNotFoundException {
        final Name nameAnnotation = surma.reflectiveProxy(Name.class,
            plugin.getAnnotation(surma.loadInjected(Name.class)));
        return nameAnnotation != null ? nameAnnotation.value() : plugin.getSimpleName();
    }
}
