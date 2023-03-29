package blue.lhf.vipu.black_magic;

import mx.kenzie.overlord.Overlord;

import java.io.*;
import java.lang.reflect.Proxy;
import java.lang.reflect.*;
import java.net.*;

/**
 * Utility for injecting JARs and classes into a {@link URLClassLoader}.
 * */
@SuppressWarnings("unchecked")
public class Surma {
    private final URLCLassLoaderAccess access;

    /**
     * @param access The {@link URLClassLoader} to inject into.
     * */
    public Surma(final URLClassLoader access) {
        this.access = new URLCLassLoaderAccess(access);
    }

    /**
     * Attempts to locate the bytecode of the given class by guessing its resource location in the class loader.
     * @return The bytecode of the given class.
     * @param clazz The class to get the bytecode of.
     * */
    protected static byte[] getBytecode(final Class<?> clazz) throws IOException {
        final ClassLoader loader = clazz.getClassLoader();
        final String path = clazz.getName().replace('.', '/') + ".class";
        try (final InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) throw new NoClassDefFoundError(
                "Surma could not locate %s in %s (looking for %s)".formatted(path, loader, clazz));
            return stream.readAllBytes();
        }
    }

    /**
     * Adds the given {@link URL} to the {@link URLClassLoader}.
     * @see URLClassLoader#addURL(URL)
     * */
    public void injectJAR(final URL url) throws InvocationTargetException, IllegalAccessException {
        access.addURL(url);
    }

    /**
     * Injects a single class into the {@link URLClassLoader}. This action is idempotent.
     * @param clazz The class to inject. The bytecode of the class must be available at <code>/name/of/Class.class</code>
     *              in the class loader of the input class.
     * */
    public Class<?> injectSingle(final Class<?> clazz) throws Exception {
        try {
            return loadInjected(clazz);
        } catch (ClassNotFoundException exception) {
            final byte[] bytes = getBytecode(clazz);
            return access.defineClass(bytes, 0, bytes.length);
        }
    }

    /**
     * @return The version of the given class that is loaded by the {@link URLClassLoader}.
     * @throws ClassNotFoundException If the given class has not been injected.
     * @param base The class to get the injected version of.
     * @see #injectSingle(Class)
     * */
    public <A> Class<A> loadInjected(final Class<?> base) throws ClassNotFoundException {
        return (Class<A>) Class.forName(base.getName(), true, access.target());
    }



    /**
     * @return A proxy of the input {@link Object} that uses reflective access to invoke methods and implements
     *         the given interface.
     * @throws IllegalArgumentException If the argument restrictions placed by {@link Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler)} are not met
     * @param theInterface The interface to implement.
     * @param target The object to proxy.
     * @see Proxy#newProxyInstance(ClassLoader, Class[], InvocationHandler)
     * */
    public <T> T reflectiveProxy(final Class<T> theInterface, final Object target) {
        if (target == null) return null;
        return (T) Proxy.newProxyInstance(theInterface.getClassLoader(), new Class<?>[]{theInterface},
            (proxy, method, args) -> {
                final Class<?>[] injectedTypes = loadInjected(method.getParameterTypes());
                return target.getClass().getMethod(method.getName(), injectedTypes).invoke(target,
                    transform(args, injectedTypes));
            });
    }

    /**
     * @return The source class of a {@link Proxy} object.
     * @param proxy The proxy to get the source class of.
     * @param <T> The type of the proxy.
     * */
    public <T> Class<?> proxySource(final T proxy) {
        try {
            return (Class<?>) Proxy.getInvocationHandler(proxy).invoke(proxy,
                Object.class.getDeclaredMethod("getClass"), new Object[0]);
        } catch (Throwable e) {
            throw new AssertionError("Could not find Object.getClass()?", e);
        }
    }

    /**
     * @return The {@link URLClassLoader} that this {@link Surma} instance is injecting into.
     * */
    public URLClassLoader getTarget() {
        return access.target();
    }

    /**
     * Maps the given array of classes to their injected versions, or themselves if they are not injected.
     * @param parameterTypes The classes to map.
     * @return The mapped classes.
     * */
    private Class<?>[] loadInjected(Class<?>[] parameterTypes) {
        if (parameterTypes == null) return null;
        final Class<?>[] injected = new Class[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            try {
                injected[i] = loadInjected(parameterTypes[i]);
            } catch (ClassNotFoundException notInjected) {
                injected[i] = parameterTypes[i];
            }
        }

        return injected;
    }

    /**
     * Forcibly transforms the given array of objects to the specified types.
     * @param args The objects to transform.
     * @param parameterTypes The types to transform the objects to.
     * @return The transformed objects.
     * */
    private Object[] transform(final Object[] args, final Class<?>[] parameterTypes) {
        if (args == null) return null;
        final Object[] output = new Object[args.length];
        for (int i = 0; i < parameterTypes.length; ++i) {
            if (parameterTypes[i].isInterface()) {
                output[i] = reflectiveProxy(parameterTypes[i], args[i]);
                continue;
            }

            output[i] = Overlord.transform(args[i], parameterTypes[i]);
        }

        return output;
    }

    private record URLCLassLoaderAccess(URLClassLoader target) {
        private static final Method addURL;
        private static final Method defineClass;

        static {
            try {
                Overlord.breakEncapsulation(Surma.class, URLClassLoader.class, true);
                Overlord.allowAccess(Surma.class, URLClassLoader.class, true);
                addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);

                Overlord.breakEncapsulation(Surma.class, ClassLoader.class, true);
                Overlord.allowAccess(Surma.class, ClassLoader.class, true);
                defineClass = ClassLoader.class.getDeclaredMethod("defineClass",
                    byte[].class, int.class, int.class);

                defineClass.setAccessible(true);
            } catch (Exception exc) {
                throw new ExceptionInInitializerError(exc);
            }
        }

        public void addURL(final URL url) throws InvocationTargetException, IllegalAccessException {
            addURL.invoke(target, url);
        }

        public Class<?> defineClass(final byte[] bytecode, final int offset, final int length)
            throws InvocationTargetException, IllegalAccessException {
            return (Class<?>) defineClass.invoke(target, bytecode, offset, length);
        }
    }
}
