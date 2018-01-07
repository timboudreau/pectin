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

import com.google.inject.name.Named;
import com.mastfrog.acteur.Acteur;
import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.Page;
import com.mastfrog.acteur.resources.ResourcesPage;
import com.mastfrog.acteur.util.HttpMethod;
import com.mastfrog.acteurbase.Chain;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.pectin.App.Acteurs;
import com.mastfrog.pectin.App.Appliable;
import com.mastfrog.pectin.PectinActeurApplication.AppliableWrapper;
import com.mastfrog.url.Path;
import io.netty.handler.codec.http.HttpResponseStatus;
import static io.netty.handler.codec.http.HttpResponseStatus.GONE;
import static io.netty.handler.codec.http.HttpResponseStatus.METHOD_NOT_ALLOWED;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
@SuppressWarnings("deprecation")
@com.mastfrog.acteur.ImplicitBindings(AppliableWrapper.class)
class PectinActeurApplication extends Application {

    @Inject
    PectinActeurApplication(App app) {
        add(OnlyPage.class);
        if (app.staticFiles != null) {
            add(ResourcesPage.class);
        }
    }

    private static class OnlyPage extends Page {

        @Inject
        OnlyPage(App app, @Named("paths") String[] paths) {
            add(CheckMethods.class);
            add(SendResponse.class);
        }

        static class CheckMethods extends Acteur {

            @Inject
            CheckMethods(HttpEvent evt, App app, @Named("paths") String[] paths, PathPatterns pp, Chain<Acteur, ? extends Chain<Acteur, ?>> chain) {
                Path path = evt.path();
                String pth = path.toString();
                String foundPath = null;
                Map<String, String> pathParams = null;
                for (String test : paths) {
                    if (pp.isExactGlob(test) && test.equals(Path.parse(pth).equals(evt.path()))) {
                        foundPath = test;
                        break;
                    } else {
                        Map<Integer, String> patterns = new HashMap<>(4);
                        Pattern p = pp.patternFor(test, patterns);
                        Matcher m = p.matcher(pth);
                        if (m.find()) {
                            foundPath = test;
                            if (!patterns.isEmpty()) {
                                pathParams = new LinkedHashMap<>();
                                for (Map.Entry<Integer, String> e : patterns.entrySet()) {
                                    String val = path.getElement(e.getKey()).toString();
                                    pathParams.put(e.getValue(), val);
                                }
                            }
                            break;
                        }
                    }
                }
                if (foundPath == null) {
                    reject();
                    return;
                }
                Set<HttpMethod> methods = app.methodsForPath.get(foundPath).keySet();
                if (methods.isEmpty() || !methods.contains(evt.method())) {
                    reply(METHOD_NOT_ALLOWED);
                    return;
                }
                Appliable a = app.methodsForPath.get(foundPath).get(evt.method());
                next(new AppliableWrapper(a, pathParams));
            }
        }

        static class SendResponse extends Acteur {

            @Inject
            SendResponse(AppliableWrapper responder, HttpEvent evt, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Dependencies deps) throws Exception {
                Object result = responder.apply(evt, chain, deps);
                if (result == null) {
                    reply(GONE);
                } else if (result instanceof HttpResponseStatus) {
                    reply((HttpResponseStatus) result);
                } else if (result instanceof CompletableFuture<?>) {
                    then(((CompletableFuture<?>) result), OK);
                } else if (result instanceof Acteurs) {
                    next();
                } else {
                    ok(result);
                }
            }
        }
    }

    static final class AppliableWrapper {

        final Appliable toApply;
        final Map<String, String> params;

        public AppliableWrapper(Appliable toApply, Map<String, String> params) {
            this.toApply = toApply;
            this.params = params == null ? Collections.emptyMap() : params;
        }

        Object apply(HttpEvent evt, Chain<Acteur, ? extends Chain<Acteur, ?>> chain, Dependencies deps) throws Exception {
            return toApply.doApply(evt, params, chain, deps);
        }
    }
}
