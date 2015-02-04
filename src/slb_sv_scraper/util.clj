(ns slb-sv-scraper.util
  (:refer-clojure :exclude [get])
  (:require [org.httpkit.client :as http]
            [net.cgrand.enlive-html :as html]
            [net.cgrand.jsoup :as jsoup]))

(defn request [method url scraper & [opts callback]]
   (http/request
     (merge {:url (str (:base-url scraper) url)
             :method method
             :headers (:request-headers scraper)
             :follow-redirects false
             :as :stream}
            opts)
     callback))

(defn get [url scraper & [opts callback]]
  (request :get url scraper opts callback))

(defn post [url scraper & [opts callback]]
  (request :post url scraper opts callback))

(defn stream->html
  "Converts a stream to an Enlive HTML node structure.

  Uses the JSoup parser as TagSoup seems to choke on some of the
  horrific markup output by SV."
  [s]
  (.reset s)
  (html/html-resource s {:parser jsoup/parser}))
