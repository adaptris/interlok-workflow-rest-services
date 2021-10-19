 # interlok-rest-proxy

This management component is designed for Heroku style deployments where you are only allowed to expose a single external port. If you have an existing API endpoint listening via _embedded jetty_ and you have also require _jolokia_ then you need to jump through additional hoops to make this possible to expose both jolokia and your API endpoint. This is a very simplified proxy simply passes through all headers as-is to the proxied URL.

Configuration is very simple, everything is done via `bootstrap.properties`. If we take the following configuration (note the use of `::` as the separator) :-

``` 
interlok.proxy.1=/jolokia::http://localhost:8081
interlok.proxy.2=/alternate::http://my.other.host:8082
```

In this instance we would register as a listener against the URI _/jolokia/*_ and _/alternate/*_ against the embedded jetty component. All traffic to our registered URI (e.g.
 http://localhost:8080/jolokia/my/jolokia/action) would be forwarded to the corresponding server/port combination (http://localhost:8081/jolokia/my/jolokia/action). Similarly traffic hitting _http://localhost:8080/alternate will also be forwarded to the corresponding server/port combination.


## Bonus Chatter

This [blog post](https://interlok.adaptris.net/blog/2017/09/04/interlok-proxy.html) forms the basis of this management component, and just encapsulates that basic behaviour as a management component.