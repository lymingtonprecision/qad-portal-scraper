(ns qad-portal-scraper.http
  (:require [org.httpkit.client :as http]))

(def ^:dynamic *max-retries* 5)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn request
  "Makes a HTTP `method` (e.g. `:get`, `:post`) request to the specified `url`
  using the session details established by the given `scraper` returning a
  future that will yield the response when dereferenced.

  `url` must be relative the the base URL of the scraper.

  An optional map of additional request options `opts` may be provided.

  Re-directs will *not* be followed and the response body will be encoded as a
  byte stream."
  [method url scraper & [opts]]
  (letfn [(make-request []
            (http/request
             (merge {:url (str (:base-url scraper) url)
                     :method method
                     :headers (:request-headers scraper)
                     :follow-redirects false
                     :timeout (* 10 1000)
                     :as :stream}
                    opts)))]
    (loop [attempt 0]
      (let [{error :error :as res} @(make-request)]
        (if (or (nil? error) (= attempt *max-retries*))
          res
          (case error
            javax.net.ssl.SSLException
            (recur (inc attempt))
            org.httpkit.client.TimeoutException
            (do
              (Thread/sleep (* attempt 1000))
              (recur (inc attempt)))
            ;; default
            (recur *max-retries*)))))))

(defn GET
  "Convenience wrapper around `request` for making `:get` requests."
  [url scraper & [opts]]
  (request :get url scraper opts))

(defn POST
  "Convenience wrapper around `request` for making `:post` requests."
  [url scraper & [opts]]
  (request :post url scraper opts))
