# qad-portal-scraper

Automates various interactions with
[QAD Supplier Portal](https://www.mfgx.net/index.jsp) sites.

Note that, due to the lack of any sort of API, this is accomplished via
replaying requests in the format seen when interacting with the site in
a browser and screen scraping the results. As such it’s liable to be
quite brittle and has been written with a view to “fail fast” rather
than deal with anything slightly anomalous.

Any failing request will result in an error being raised using `ex-info` with
the `:type` set to an appropriate value to indicate exactly which request failed
and with as much additional data included in the `ex-data` map as possible
(including the full HTTP response.)

## Usage

Add the library to your `project.clj` dependencies:

[![Clojars Project](https://img.shields.io/clojars/v/lymingtonprecision/qad-portal-scraper.svg)](https://clojars.org/lymingtonprecision/qad-portal-scraper)

Create a session and retrieve an order, print the number of lines:

```clojure
(ns sp-download
  (:require [qad-portal-scraper.session :as session]
            [qad-portal-scraper.order :as order])

(with-open [s (session/login "http://mfgx.example.com" "username" "password")]
  (let [o (order/fetch-order s "123456")]
    (println (count (:order/lines o)))))
```

Retrieve the BOM for the first item on the order, saving the PDF report to a
temp file, and printing and print a nested map of the components actually used
in the build:

```clojure
(ns sp-download
  (:require [qad-portal-scraper.session :as session]
            [qad-portal-scraper.order :as order]
            [qad-portal-scraper.bom :as bom]
            [clojure.java.io :as io]
            [clojure.pprint :refer [pprint])

(with-open [s (session/login "http://mfgx.example.com" "username" "password")]
  (let [o (order/fetch-order s "123456")
        item (-> o :order/lines vals first :line/item)
        tmp (java.io.File/createTempFile (str (:item/id item) "-BOM") ".pdf")]
    (-> (bom/fetch-bom-pdf s item)
        io/input-stream
        (io/copy tmp))
    (pprint (bom/fetch-bom s item))
    (pprint (str "saved PDF in " (.getPath tmp)))))
```

Refer to [the schema][schema] for details of the various record structures
returned by the `fetch-*` functions and [the API documentation][api-docs] for
everything else.

[schema]: src/qad_portal_scraper/schema.clj
[api-docs]: https://lymingtonprecision.github.io/qad-portal-scraper

## On the Lack of Idempotency of Filters and Other Causes of Conflict

One important thing to note about interacting with the portal by mimicking user
interaction is that various actions result in a mutation of shared state that
applies _not_ to the _session_ but to the _user_. The most prevalent example of
this is setting filters.

This library tries to work around this as much as possible (by re-submitting the
entire set of filter parameters on each page request for example) but you should
be aware that running multiple concurrent sessions under a single user account
may give unexpected results.

## License

Copyright © 2016 Lymington Precision Engineers Co. Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
