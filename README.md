Pectin
======

A simplified, lambda-oriented library for writing Java http clients, mostly written to
prove you could build something like this over Acteur, using async i/o and a tiny
footprint.

In particular, it's API makes it possible to write exactly the same code you see at
the top of [Javalin's web site](https://javalin.io/):

```
        App app = App.create()
                .enableStaticFiles("/public")
                .enableStandardRequestLogging()
                .port(port)
                .start();

        app.routes(() -> {
            path("users", () -> {
                get(UserController::getAllUserIds);
                post(UserController::createUser);
                path(":user-id", () -> {
                    delete(UserController::deleteUser);
                    get(UserController::getUser);
                });
            });
        });
```

What It's Good For
==================

Well, writing code like that.

Handlers for HTTP methods may be any one of a small mountain of functional interfaces defined on App,
for accepting the event, path parameters, the payload bytes or most combinations of those.

A handler may return:

 * An object, which will be rendered as JSON unless it's a `byte[]`, `ByteBuf` or `String`
 * An `HttpResponseStatus` which will be returned as the status code with no body
 * A `CompletableFuture` in which case the response will be the eventual output of that future
 * Null, resulting in a `410 Gone` response


Caveats
=======

APIs like this are seductively pretty, and are that way because they make the problem of writing
an HTTP server simpler than it is.  For a quick-and-dirty REST server, that may be adequate, but
real-world applications, it quickly turns out that what you actually need to do it right is 
much different:

 * If you want to scale and serve mobile clients on spotty connections, any API _must_ deal with
cache headers.  And that goes equally for the server-side - if you can look at an `If-Modified-Since`
and perform a tiny query on your database rather than return 10k rows just to find out that the
only response needed is `304 Not Modified`.  An API that is a linear path from request to response
makes that difficult - those pretty little one-liner lambdas get hairy and ugly quickly, and the
ability to isolate, compose and reuse that logic would be handy.

 * Use of dependency injection becomes doable, but far less natural than in Acteur - if your
member reference needs an injected database connection, you need to:

   * Define that as a constructor parameter of the object that owns the methods you'll turn into lambdas
   * Remember to use `Provider`, since that object _must_ be a singleton (you're referencing methods
on it at startup time), so, for example, you don't hold single connection from a connection pool,
use it for everything, and block it from being replaced if the network goes down
   * You need a reference to that object as soon as the application starts, to plumb your REST API - if
you need objects like that at startup time, you're not really _using_ dependency injection in a meaningful
way anymore


Implementation Notes
--------------------

Things would be a lot simpler if you could write:

```
public void get(Function<T,Object> createResponse) { ... }
```

and have any hope of recovering the type of T at runtime (with an anonymous inner class you might
be able to;  with a lambda, there is no possibility of that, including using annotations to explicitly
provde it).

So, there is a pile of interfaces defined on `App` which provide for various combinations of input
arguments.

Similarly, it would be nice if you could at least write:

```
public <T> void get(Function<HttpEvent, T> createResponse) { ... }
```

to allow `get` to return something you could chain further things against the output of, or otherwise
provide some more nuanced, typed control-flow (for example, conditionally executing logic based on the
previous result).  However, with that signature, Java member-references become unusable because the
compiler cannot (though it has enough information to) infer the type of T from the return type of the
member.

At any rate, it's nifty and provides a terse way of writing tinker-toy prototypes.

Why "Pectin"?
-------------

It's a thing used in making jelly and jam - it facilitates making something sweet and tasty, but its
role is simply to stiffen it a bit.  Seemed about right.

