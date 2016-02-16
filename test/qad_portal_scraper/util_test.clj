(ns qad-portal-scraper.util-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.java.io :as io]
            [qad-portal-scraper.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; extract-context-key

(deftest extract-context-key
  (let [url (io/resource "orders/singleline-order.htm")
        html (slurp url)
        exp {:context (last (re-find #"slc_CONTEXT_KEY\" value=\"([^\"]+)" html))
             :key (last (re-find #"slc_KEY\" value=\"([^\"]+)" html))}]
    (with-open [page (io/input-stream url)]
      (is (= exp (util/extract-context-key (util/stream->html page)))))))
