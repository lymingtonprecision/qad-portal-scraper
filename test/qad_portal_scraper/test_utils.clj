(ns qad-portal-scraper.test-utils
  (:require [clojure.java.io :as io]
            [qad-portal-scraper.util :as util]))

(defn html-resource [path]
  (with-open [raw-html (io/input-stream (io/resource path))]
    (util/stream->html raw-html)))
