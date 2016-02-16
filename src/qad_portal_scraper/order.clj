(ns qad-portal-scraper.order
  (:require [schema.core :as s]
            [qad-portal-scraper.schema :refer [Order OrderID]]
            [qad-portal-scraper.filters :as filters]
            [qad-portal-scraper.http :as http]
            [qad-portal-scraper.util :as util]
            [qad-portal-scraper.order.parse :as parse]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default URL Paths

(def ^:dynamic *orders-url* "sv/orders/list-orders.do")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants

(def order-attributes-to-request
  (keys parse/order-attribute->record-key))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Request utility fns

(defn context-key
  "Retrieves the current order context key from the portal for the session
  established by the scraper. Throws an error if the request fails."
  [scraper]
  (let [resp (http/GET *orders-url* scraper)]
    (if (= 200 (:status resp))
      (util/extract-context-key (:body resp))
      (throw (ex-info "failed to get order page for context key"
                      {:type ::fetch-order-context
                       :scraper scraper
                       :http-response resp})))))

(defn filters-and-context
  "Returns a tuple of the current `[filters context]` in use on the orders page
  for the provided scraper session."
  [scraper]
  (let [resp (http/POST *orders-url* scraper
               {:form-params {"activeTab" "filter"
                              "lastTab" "filter"
                              "lastTabSupport" "data"}})]
    (if (= 200 (:status resp))
      (let [page (util/stream->html (:body resp))]
        [(filters/from-page page)
         (util/extract-context-key page)])
      (throw (ex-info
              "failed to fetch order filter page"
              {:type ::fetch-order-filter
               :http-response resp
               :scraper scraper})))))

(defn fetch-data-params
  "Returns the parameters required to retrieve the orders matching the provided
  `filters`."
  [context filters]
  (merge
   {"slc_KEY" (:key context)
    "slc_CONTEXT_KEY" (:context context)
    "activeTab" "data"
    "lastTab" "data"
    "lastTabSupport" "filter"
    "applyAtts" "true"
    (util/in-context "slc_ATT_REORDER" context) order-attributes-to-request
    (util/in-context "slc_PAGE" context) 0
    (util/in-context "slc_PAGE_SIZE" context) "100"
    (util/in-context "slc_CND_ACTION" context) "replace"}
   (filters/params filters context)))

(defn update-page-number
  "Replaces the requested page number in the provided `params` map with page
  `n`."
  [params context n]
  {:pre [(pos? n)]}
  (assoc params (util/in-context "slc_PAGE" context) (dec n)))

(defn fetch-order-page
  [scraper params & ex-info]
  (let [resp (http/POST *orders-url* scraper {:form-params params})]
    (if (= 200 (:status resp))
      (util/stream->html (:body resp))
      (throw (ex-info
              (str "failed to fetch order")
              (merge {:type ::fetch-order
                      :http-response resp
                      :scraper scraper
                      :params params}
                     ex-info))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn fetch-order :- (s/maybe Order)
  "Retrieves the specified order details. Throws an error if a request fails."
  [scraper order :- OrderID]
  (let [[filters cntx] (filters-and-context scraper)
        order-filter (-> (filters/unset-all filters)
                         (assoc-in ["OrderNumber" :value] order))
        params (fetch-data-params cntx order-filter)
        fetch-page #(fetch-order-page
                     scraper
                     (update-page-number params cntx %)
                     {:order order :page %})
        first-page (fetch-page 1)
        pages (reduce
               (fn [rs page-num]
                 (conj rs (fetch-page page-num)))
               [first-page]
               (rest (parse/page-range first-page)))
        headers (parse/column-headings first-page)]
    (-> (reduce #(parse/orders %2 %1 headers) {} pages)
        (get order))))
