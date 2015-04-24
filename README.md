# webvis

A visualy stimulating web crawler. Displays a live updating graph of all domains visited.

## How to Build

Requires [leiningen](http://leiningen.org/).

Run `lein uberjar` or `lein bin`

## Usage

### CLI

```
./target/webvis <URL> [-h --help] [-d --depth] [-b --blacklist] [-w --workers] [-c --concurrency]
````

The crawler will begin crawling at the url provided.

If a depth is specified, it will crawl no more than the specified depth, where the root domain is of depth 0. The depth is the number of _domains_ crawled from the root domain, not the number of URLS crawled from the root URL.

The number of worker threads and the maximum number of concurrent requests can also be set. Usually only 1 worker is needed. A lower request concurrency is preferable so the crawler won't overload any servers. The default number of workers is 1 and the default maximum number of requests is 2.

Blacklisted domains will not be crawled.

### REPL

To create a spider:

create-spider: [max-concurrent-reqs blacklist] [max-concurrent-reqs]
```clojure

(def spider (create-spider 2 [facebook.com yahoo.com]))

```

The spider can then begin crawling with:

build-web: [spider url worker-count max-depth]
```clojure

(build-web spider "http://example.com" 1 4)

```

A max depth of -1 will cause the spider to crawl forever.

To stop the spider from crawling:

```clojure
(freeze! spider)
```

This will remove all workers. The spider will start back again once another worker is added.

```clojure
(spawn-worker spider)
```

To kill a spider, rendering it forever unusable:

```clojure
;; eek!
(kill! spider)
```

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License v1.0 (https://www.eclipse.org/legal/epl-v10.html)
