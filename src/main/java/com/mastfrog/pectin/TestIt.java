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

import static com.google.common.base.Charsets.UTF_8;
import com.mastfrog.acteur.HttpEvent;
import static com.mastfrog.pectin.App.delete;
import static com.mastfrog.pectin.App.get;
import static com.mastfrog.pectin.App.path;
import com.mastfrog.util.strings.RandomStrings;
import io.netty.buffer.ByteBuf;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Tim Boudreau
 */
public class TestIt {

    public static void main(String[] args) throws Exception {
        int port = 8192;
        App app = App.create()
                .enableStaticFiles("public")
                .enableStandardRequestLogging()
                .port(port)
                .start();

        app.routes(() -> {
            path("users", () -> {
                get(UserController::getAllUserIds);
                App.<String>post(UserController::createUser);
                path(":user-id", () -> {
                    delete(UserController::deleteUser);
                    get(UserController::getUser);
                });
            });
        });
    }

    public static final class UserController {

        private static final Map<String, String> users = new HashMap<>();
        private static final RandomStrings ids = new RandomStrings();

        public static Map<String, String> getAllUserIds() {
            return users;
        }

        public static String createUser(ByteBuf content) {
            String result = ids.get(12);
            users.put(result, content.readCharSequence(content.readableBytes(), UTF_8).toString());
            return result;
        }

        public static Object getUser(String id) {
            return users.get(id);
        }

        public static boolean updateUser(HttpEvent evt) throws IOException {
            String oldName = users.get(evt.path().lastElement().toString());
            if (oldName != null) {
                users.put(evt.path().lastElement().toString(), evt.stringContent());
                return true;
            }
            return false;
        }

        public static String deleteUser(String user) {
            return users.remove(user);
        }
    }
}
