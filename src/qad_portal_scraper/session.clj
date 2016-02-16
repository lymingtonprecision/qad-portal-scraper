(ns qad-portal-scraper.session
  (:require [clj-time.core :as time]
            [qad-portal-scraper.http :as http]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default URL Paths

(def ^:dynamic *user-index-url* "sv/user-index.jsp")
(def ^:dynamic *login-url* "sv/mfgx/login.jsp")
(def ^:dynamic *login-post-url* "sv/mfgx/j_security_check")
(def ^:dynamic *logout-url* "sv/logout.do")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defn redirect-to-url?
  "Returns true if the given `http-response` map is a redirect to the specified
  `url`."
  [url {:keys [status headers error] :as http-response}]
  (and (nil? error)
       (= 302 status)
       (re-find (re-pattern url) (:location headers))))

(def redirect-to-login? (partial redirect-to-url? *login-url*))
(def redirect-to-user-index? (partial redirect-to-url? *user-index-url*))

(defn update-session
  "Updates the session details of a scraper from those provided by the server in
  the given HTTP `response`.

  Returns a copy of `scraper` with the new session details"
  [scraper {:keys [headers] :as response}]
  (let [c (:set-cookie headers)
        id (second (re-find #"=([A-Za-z0-9]+);" c))]
    (-> scraper
        (assoc :session-id id)
        (assoc-in [:request-headers "Cookie"] c))))

(defn establish-session
  "Establishes/validates the portal session of a scraper.

  Returns the scraper if it represents a valid session, a copy of the scraper
  with new session details, or returns a `::session-failure` error."
  [scraper]
  (let [resp (http/GET *user-index-url* scraper)]
    (cond
      (-> resp :headers :set-cookie) (update-session scraper resp)
      (contains? #{200 302} (:status resp)) scraper
      :else (throw (ex-info "invalid session"
                            {:type ::session-failure
                             :scraper scraper
                             :http-response resp})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defrecord Scraper [base-url]
  java.io.Closeable
  (close [this]
    (when (:session-id this)
      (http/GET *logout-url* this)
      (assoc this :session-id nil :logged-in-as nil :logged-in-at nil))))

(defn scraper
  "Create a scraper instance for keeping track of session state"
  [base-url]
  (->Scraper base-url))

(defn login
  "Logs in to the portal using the specified credentials, returning an updated
  scraper, or throwing a `::login-failure` error if any part of the request
  fails.

  `scraper-or-url` can either be an existing scraper record or the base URL
  string of the portal.

  The recommended way to use scrapers is inside a `with-open` block so that the
  associated state is nicely encapsulated:

      (with-open [s (session/login portal-url username password)]
        ...use session...)

  Scrapers implement the `java.io.Closeable` interface and will logout of the
  portal when `.close`d."
  [scraper-or-url username password]
  (let [s (-> (if (:base-url scraper-or-url)
                scraper-or-url
                (scraper scraper-or-url))
              establish-session)
        resp (http/POST *login-post-url* s
               {:form-params {"j_username" username
                              "j_password" password}})]
    (if (redirect-to-user-index? resp)
      (assoc s :logged-in-as username :logged-in-at (time/now))
      (throw (ex-info "login failed"
                      {:type ::login-failure
                       :scraper scraper
                       :credentials {:username username :password password}
                       :http-response resp})))))

(defn logout
  "Logs out of the session associated with the provided scraper,
  returning a new scraper instance using the same base URL."
  [scraper]
  (.close scraper))
