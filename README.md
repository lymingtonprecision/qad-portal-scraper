# qad-portal-scraper

Automates various interactions with
[QAD Supplier Portal](https://www.mfgx.net/index.jsp) sites.

Note that, due to the lack of any sort of API, this is accomplished via
replaying requests in the format seen when interacting with the site in
a browser and screen scraping the results. As such it's liable to be
quite brittle and has been written with a view to "fail fast" rather
than deal with anything slightly anomalous.

Any failing request will result in an error being raised using the
[Slingshot](https://github.com/scgilardi/slingshot) library `throw+`
method. The resulting exception will contain the full HTTP response to
aid in debugging.

## Usage

Add the library to your `project.clj` dependencies:

```clojure
:dependencies [["qad-portal-scraper" "0.1.0"]]
```

Create a session and retrieve an order, print the number of lines:

```clojure
(ns sp-download
  (:require [qad-portal-scraper.session :refer [with-scraper-session]]
            [qad-portal-scraper.order :as order])

(with-scraper-session [s "http://mfgx.example.com" "username" "password"]
  (let [o (order/get-order s "123456")]
    (println (count o))))
```

Retrieve the BOM for the first item on the order and print a nested map
of the components actually used in the build:

```clojure
(ns sp-download
  (:require [qad-portal-scraper.session :refer [with-scraper-session]]
            [qad-portal-scraper.order :as order]
            [qad-portal-scraper.item :as item]
            [qad-portal-scraper.bom-util :as bu]
            [clojure.pprint :refer [pprint])

(with-scraper-session [s "http://mfgx.example.com" "username" "password"]
  (let [o (order/get-order s "123456")
        b (item/get-bom s (-> o first (get "ItemID")))]
    (pprint (bu/filter-by-useable b))))
```

## License

Copyright © 2015 Lymington Precision Engineers Co. Ltd.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
