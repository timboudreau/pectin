/*
 * The MIT License
 *
 * Copyright 2018 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.mastfrog.pectin;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.RequestLogger;
import com.mastfrog.acteur.resources.Resource;
import com.mastfrog.acteur.resources.ResourcesPage;
import com.mastfrog.acteur.resources.StaticResources;
import com.mastfrog.acteur.resources.DynamicFileResources;
import static com.mastfrog.acteur.headers.Method.DELETE;
import static com.mastfrog.acteur.headers.Method.GET;
import static com.mastfrog.acteur.headers.Method.POST;
import static com.mastfrog.acteur.headers.Method.PUT;
import com.mastfrog.acteur.server.ServerModule;
import com.mastfrog.acteur.util.HttpMethod;
import com.mastfrog.acteur.util.RequestID;
import com.mastfrog.acteur.util.Server;
import com.mastfrog.acteur.util.ServerControl;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.scope.ReentrantScope;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.Checks;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.function.ThrowingBiFunction;
import com.mastfrog.util.function.ThrowingFunction;
import com.mastfrog.util.function.ThrowingRunnable;
import com.mastfrog.util.function.ThrowingSupplier;
import com.mastfrog.util.function.ThrowingTriFunction;
import com.mastfrog.util.thread.NonThrowingAutoCloseable;
import com.mastfrog.util.thread.ProtectedThreadLocal;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class App {

    private static final ProtectedThreadLocal<App> APP = new ProtectedThreadLocal<>();
    private static final ProtectedThreadLocal<String> PATH = new ProtectedThreadLocal<>();

    final Map<String, Map<HttpMethod, Appliable>> methodsForPath
            = CollectionUtils.supplierMap(() -> {
                return new HashMap<>();
            });

    String staticFiles;
    boolean logging;
    int port = 8080;

    public static App create() {
        return new App();
    }
    private ServerControl ctrl;

    public App enableStaticFiles(String path) {
        staticFiles = path;
        return this;
    }

    public App enableStandardRequestLogging() {
        logging = true;
        return this;
    }

    public App port(int port) {
        this.port = port;
        return this;
    }

    public void routes(ThrowingRunnable setupPaths) {
        try (NonThrowingAutoCloseable ac = APP.set(this)) {
            setupPaths.run();
        } catch (Exception e) {
            Exceptions.chuck(e);
        }
    }

    public static void path(String path, ThrowingRunnable setupMethods) {
        Checks.notNull("app", APP.get());
        String currPath = PATH.get();
        String newPath = currPath == null ? path : currPath + '/' + path;
        try (NonThrowingAutoCloseable ac = PATH.set(newPath)) {
            setupMethods.run();
        } catch (Exception ex) {
            Exceptions.chuck(ex);
        } finally {
            PATH.set(currPath);
        }
    }

    private static App add(HttpMethod method, Appliable handler) {
        Checks.notNull("app", APP.get());
        Checks.notNull("path", PATH.get());
        APP.get().methodsForPath.get(PATH.get()).put(method, handler);
//        System.out.println("ADD " + method.name() + " " + PATH.get() + " with " + handler);
        return APP.get();
    }

    void foo() {

    }

    public static void get(JustRespond method) {
        add(GET, method);
    }

    public static void get(JustEvent method) {
        add(GET, method);
    }

    public static void get(PathParameter method) {
        add(GET, method);
    }

    public static void get(EventAndPathParameter method) {
        add(GET, method);
    }

    public static void get(PathParameters method) {
        add(GET, method);
    }

    public static void get(EventAndPathParameters method) {
        add(GET, method);
    }

    public static void get(Class<? extends Acteur>... acteurs) {
        add(GET, new Acteurs(acteurs));
    }

    public static void delete(JustEvent method) {
        add(DELETE, method);
    }

    public static void delete(PathParameter method) {
        add(DELETE, method);
    }

    public static void delete(EventAndPathParameter method) {
        add(DELETE, method);
    }

    public static void delete(PathParameters method) {
        add(DELETE, method);
    }

    public static void delete(EventAndPathParameters method) {
        add(DELETE, method);
    }

    public static void delete(Class<? extends Acteur>... acteurs) {
        add(DELETE, new Acteurs(acteurs));
    }

    public static void put(JustEvent method) {
        add(PUT, method);
    }

    public static void put(JustContent method) {
        add(PUT, method);
    }

    public static void put(PathParameter method) {
        add(PUT, method);
    }

    public static void put(EventAndPathParameter method) {
        add(PUT, method);
    }

    public static void put(PathParameters method) {
        add(PUT, method);
    }

    public static void put(EventAndPathParameters method) {
        add(PUT, method);
    }

    public static void put(EventAndContentAndPathParameters method) {
        add(PUT, method);
    }

    public static <T> void put(ThrowingFunction<T, Object> method, Class<T> type) {
        add(PUT, new JustTypedContentImpl(type, method));
    }

    public static void put(Class<? extends Acteur>... acteurs) {
        add(PUT, new Acteurs(acteurs));
    }

    public static void post(JustContent method) {
        add(POST, method);
    }

    public static void post(JustEvent method) {
        add(POST, method);
    }

    public static void post(PathParameter method) {
        add(POST, method);
    }

    public static void post(EventAndPathParameter method) {
        add(POST, method);
    }

    public static void post(PathParameters method) {
        add(POST, method);
    }

    public static void post(EventAndPathParameters method) {
        add(POST, method);
    }

    public static void post(EventAndContentAndPathParameters method) {
        add(POST, method);
    }

    public static <T> void post(ThrowingFunction<T, Object> method, Class<T> type) {
        add(POST, new JustTypedContentImpl(type, method));
    }

    public static void post(Class<? extends Acteur>... acteurs) {
        add(POST, new Acteurs(acteurs));
    }

    public static void patch(JustContent method) {
        add(PATCH, method);
    }

    public static void patch(JustEvent method) {
        add(PATCH, method);
    }

    public static void patch(PathParameter method) {
        add(PATCH, method);
    }

    public static void patch(EventAndPathParameter method) {
        add(PATCH, method);
    }

    public static void patch(PathParameters method) {
        add(PATCH, method);
    }

    public static void patch(EventAndPathParameters method) {
        add(PATCH, method);
    }

    public static void patch(EventAndContentAndPathParameters method) {
        add(PATCH, method);
    }

    public static <T> void patch(ThrowingFunction<T, Object> method, Class<T> type) {
        add(POST, new JustTypedContentImpl(type, method));
    }

    public static void patch(Class<? extends Acteur>... acteurs) {
        add(POST, new Acteurs(acteurs));
    }

    public App stop() throws InterruptedException {
        if (ctrl != null) {
            ctrl.shutdown(true);
        }
        return this;
    }

    public App start() {
        try {
            SettingsBuilder sb = new SettingsBuilder()
                    .add(ServerModule.PORT, port);
            if (staticFiles != null) {
                String sf = staticFiles;
                if (sf.length() > 0 && sf.charAt(0) == '/') {
                    sf = sf.substring(1);
                }
                if (sf.length() > 0 && sf.charAt(sf.length()-1) != '/') {
                    sf += '/';
                }
                sf += "(.*)";
                sb.add(ResourcesPage.SETTINGS_KEY_STATIC_RESOURCES_BASE_URL_PATH, sf);
            }
            Settings settings = sb.build();
            ReentrantScope scope = new ReentrantScope();
            Dependencies deps = Dependencies.builder().add(new ServerModule(scope, PectinActeurApplication.class, 8, 3, 2),
                    new Module() {
                public void configure(Binder binder) {
                    binder.bind(App.class).toInstance(App.this);
                    binder.bind(String[].class).annotatedWith(Names.named("paths")).toInstance(methodsForPath.keySet().toArray(new String[0]));
                    binder.bind(PathPatterns.class).toInstance(new PathPatterns());
                    if (App.this.staticFiles != null) {
                        File dir = new File(".").getAbsoluteFile().toPath().normalize().toFile();
                        System.out.println("Serving files from " + dir + " on " + App.this.staticFiles);
                        binder.bind(File.class).toInstance(dir);
                        binder.bind(StaticResources.class).to(DynamicFileResources.class);
                        scope.bindTypes(binder, Resource.class);
                    }
                    if (!App.this.logging) {
                        binder.bind(RequestLogger.class).toInstance(new RequestLogger() {
                            @Override
                            public void onBeforeEvent(RequestID rid, Event<?> event) {
                                // do nothing
                            }

                            @Override
                            public void onRespond(RequestID rid, Event<?> event, HttpResponseStatus status) {
                                // do nothing
                            }
                        });
                    }
                }
            }).add(settings).build();
            Thread t = new Thread(() -> {
                ServerControl ctrl;
                try {
                    System.out.println("Starting on port " + port);
                    App.this.ctrl = deps.getInstance(Server.class).start();
                    System.out.println("Started");
                    App.this.ctrl.await();
                } catch (Exception ex) {
                    Exceptions.chuck(ex);
                }
            });
            t.setDaemon(false);
            t.start();
            return this;
        } catch (Exception ex) {
            return Exceptions.chuck(ex);
        }
    }

    static final HttpMethod PATCH = new HttpMethod() {
        @Override
        public String name() {
            return "PATCH";
        }

        @Override
        public CharSequence toCharSequence() {
            return name();
        }
    };

    public interface JustRespond extends ThrowingSupplier<Object>, Appliable {

    }

    public interface EventAndContent extends ThrowingBiFunction<HttpEvent, ByteBuf, Object>, Appliable {

    }

    public interface EventAndContentAndPathParameter extends ThrowingTriFunction<HttpEvent, ByteBuf, String, Object>, Appliable {

    }

    public interface EventAndContentAndPathParameters extends ThrowingTriFunction<HttpEvent, ByteBuf, Map<String, String>, Object>, Appliable {

    }

    public interface JustEvent extends ThrowingFunction<HttpEvent, Object>, Appliable {

    }

    public interface JustContent extends ThrowingFunction<ByteBuf, Object>, Appliable {

    }

    public interface EventAndPathParameter extends ThrowingBiFunction<HttpEvent, String, Object>, Appliable {

    }

    public interface PathParameter extends ThrowingFunction<String, Object>, Appliable {

    }

    public interface EventAndPathParameters extends ThrowingBiFunction<HttpEvent, Map<String, String>, Object>, Appliable {

    }

    public interface PathParameters extends ThrowingFunction<Map<String, String>, Object>, Appliable {

    }

    public interface JustTypedContent<T> extends ThrowingFunction<T, Object>, Appliable {

        Class<T> type();
    }

    static class Acteurs<T> implements Appliable {

        private final List<Class<? extends Acteur>> l = new ArrayList<>(3);

        @SafeVarargs
        Acteurs(Class<? extends Acteur>... types) {
            l.addAll(Arrays.asList(types));
        }

        @Override
        public Object doApply(HttpEvent evt, Map<String, String> pathParameters, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Dependencies deps) throws Exception {
            for (Class<? extends Acteur> c : l) {
                Acteur a = Acteur.wrap(c, deps);
                chain.add(c);
            }
            return this;
        }

    }

    static class JustTypedContentImpl<T> implements JustTypedContent<T>, Appliable {

        private final Class<T> type;
        private final ThrowingFunction<T, Object> func;

        public JustTypedContentImpl(Class<T> type, ThrowingFunction<T, Object> func) {
            this.type = type;
            this.func = func;
        }

        @Override
        public Class<T> type() {
            return type;
        }

        @Override
        public Object apply(T arg) throws Exception {
            return func.apply(arg);
        }
    }

    static final <T> Object applyTyped(JustTypedContent<T> cont, HttpEvent evt) throws Exception {
        return cont.apply(evt.jsonContent(cont.type()));
    }

    public interface Appliable {

        public default Object doApply(HttpEvent evt, Map<String, String> pathParameters, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Dependencies deps) throws Exception {
            // Not the ideal pattern, since this changes the semantics of the superclass based on its
            // subtype - nicer would be a pluggable registry of Appliable appliers - buut will do for now,
            // as we're going for a low-complexity API
            if (this instanceof JustRespond) {
                return ((JustRespond) this).get();
            } else if (this instanceof EventAndContent) {
                return ((EventAndContent) this).apply(evt, evt.content());
            } else if (this instanceof JustEvent) {
                return ((JustEvent) this).apply(evt);
            } else if (this instanceof JustContent) {
                return ((JustContent) this).apply(evt.content());
            } else if (this instanceof EventAndPathParameter) {
                return ((EventAndPathParameter) this).apply(evt, pathParameters.get(pathParameters.keySet().iterator().next()));
            } else if (this instanceof PathParameter) {
                return ((PathParameter) this).apply(pathParameters.get(pathParameters.keySet().iterator().next()));
            } else if (this instanceof PathParameter) {
                return ((PathParameter) this).apply(evt.stringContent());
            } else if (this instanceof EventAndPathParameters) {
                return ((EventAndPathParameters) this).apply(evt, pathParameters);
            } else if (this instanceof PathParameters) {
                return ((PathParameters) this).apply(pathParameters);
            } else if (this instanceof JustTypedContent<?>) {
                return applyTyped((JustTypedContent<?>) this, evt);
            } else if (this instanceof EventAndContentAndPathParameter) {
                return ((EventAndContentAndPathParameter) this).apply(evt, evt.content(), pathParameters.get(pathParameters.keySet().iterator().next()));
            } else {
                throw new AssertionError("Don't know how to call apply() on a " + getClass().getSimpleName());
            }
        }
    }
}
