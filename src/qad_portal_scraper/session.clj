(ns qad-portal-scraper.session
  (:require [org.httpkit.client :as http]
            [clj-time.core :refer [now]]
            [slingshot.slingshot :refer [throw+]]
            [qad-portal-scraper.util :as util]))

(def ^:dynamic *user-index-url* "sv/user-index.jsp")
(def ^:dynamic *login-url* "sv/mfgx/login.jsp")
(def ^:dynamic *login-post-url* "sv/mfgx/j_security_check")
(def ^:dynamic *logout-url* "sv/logout.do")

(defn redirect-to-url?
  [url {:keys [status headers error]}]
  (and (nil? error)
       (= 302 status)
       (re-find (re-pattern url) (:location headers))))

(def redirect-to-login? (partial redirect-to-url? *login-url*))
(def redirect-to-user-index? (partial redirect-to-url? *user-index-url*))

(defn create-scraper
  "Create a scraper instance for keeping track of session state"
  [base-url]
  {:base-url base-url
   :session-id nil
   :request-headers {}
   :logged-in-as nil
   :logged-in-at nil})

(defn set-session
  "Set the session details of a scraper
  Returns a copy of `scraper` with the new session details"
  [scraper {:keys [headers]}]
  (let [c (:set-cookie headers)
        id (second (re-find #"=([A-Za-z0-9]+);" c))
        s (assoc scraper :session-id id)]
    (update-in s [:request-headers] #(assoc % "Cookie" c))))

(defn establish-session
  "Establishes/validates the SV session of a scraper

  Returns the scraper if it represents a valid session, a copy of the
  scraper with new session details, or throws an error"
  [scraper]
  (let [url (str (:base-url scraper) *user-index-url*)
        resp @(http/get url {:headers (:request-headers scraper)
                             :follow-redirects false})]
    (cond
      (-> resp :headers :set-cookie) (set-session scraper resp)
      (contains? #{200 302} (:status resp)) scraper
      :else (throw+ {:type ::session-failure
                     :scraper scraper
                     :http-response resp}))))

(defn login
  "Logs in to SV using the specified credentials, returning an updated
  scraper or throwing an error if any part of the request fails"
  [scraper username password]
  (let [s (establish-session scraper)
        url (str (:base-url s) *login-post-url*)
        resp @(http/post url {:headers (:request-headers s)
                              :form-params {"j_username" username
                                            "j_password" password}
                              :follow-redirects false})]
    (if (redirect-to-user-index? resp)
      (assoc s :logged-in-as username :logged-in-at now)
      (throw+ {:type ::login-failure
               :scraper scraper
               :credentials {:username username :password password}
               :http-response resp}))))

(defn logout
  "Logs out of the session associated with the provided scraper,
  returning a new scraper instance using the same base URL"
  [scraper]
  (do
    @(util/get *logout-url* scraper)
    (create-scraper (:base-url scraper))))

(defmacro with-scraper-session
  "Creates a scraper logged in as the specified user, binding it to
  `session-name` within the provided body."
  [[session-name base-url username password] & body]
  `(let [scraper# (create-scraper ~base-url)
         ~session-name (login scraper# ~username ~password)
         r# (doall ~@body)]
     (logout ~session-name)
     r#))
