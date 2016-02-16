(ns qad-portal-scraper.util
  (:require [clojure.string :as string]
            [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn nil-if-blank [s]
  (if (string/blank? s) nil s))

(defn remove-nbsp [s]
  (string/replace s #"\xa0" ""))

(defn str->bool [s]
  (case (some-> s str string/lower-case)
    "yes" true
    "y" true
    false))

(defn kebab-keyword [s]
  (some-> s str string/lower-case (string/replace #"_" "-") keyword))

(defn stream->html
  "Converts a stream to an Enlive HTML node structure.

  Uses the JSoup parser as TagSoup seems to choke on some of the
  horrific markup output by SV."
  [^java.io.InputStream s]
  (when (.markSupported s)
    (.mark s 0)
    (.reset s))
  (html/html-resource s {:parser jsoup/parser}))

(defn extract-context-key
  "Returns a map of the `:context` and `:key` from the given page that should be
  submitted on future requests."
  [page]
  (let [k (html/select page [[:input (html/attr-ends :name "_KEY")]])
        km (reduce (fn [m {{n :name v :value} :attrs}] (assoc m n v)) {} k)]
    {:context (get km "slc_CONTEXT_KEY")
     :key (get km "slc_KEY")}))

(defn in-context
  "Returns the named `param`eter suitable for use in a request under `context`."
  [param context]
  (str param "_" (:key context)))

(defn link-params
  "Returns a map of parameter names and values from a portal navigation link.

  (The links are actually Javascript blobs that update and submit forms,
  *sigh*.)"
  [href]
  (some->>
   href
   (re-seq #"setParam\(document\.getElementById\('fmain'\),'([A-Za-z]+)','([^']+)'\);")
   (reduce (fn [r [_ k v]] (assoc r k v)) {})))
