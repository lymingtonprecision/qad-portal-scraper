(ns qad-portal-scraper.bom
  (:require [clojure.string :as string]
            [clj-time.coerce :as time.coerce]
            [net.cgrand.enlive-html :as html]
            [schema.core :as s]
            [qad-portal-scraper.bom.parse :as parse]
            [qad-portal-scraper.schema :refer [BOM]]
            [qad-portal-scraper.http :as http]
            [qad-portal-scraper.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default URL Paths

(def ^:dynamic *bom-url* "sv/item/BOMReport.do")
(def ^:dynamic *report-url* "sv/report.do")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defn form-params
  [item-ids]
  {"itemPlaceSysID" (get item-ids "itemPlaceSysID")
   "entitySysID" (get item-ids "itemPlaceSysID")
   "orgSysID" (some #(get item-ids %) ["pOrgSysID" "orgSysID" "customerOrgSysID"])
   "level" "999"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn fetch-bom :- (s/maybe BOM)
  "Retrieves the Bill Of Materials for an item in SV, returning a nested map of
  the components, `nil` if the item has no components, or throwing an error if
  the request fails."
  [scraper {item-ids :qad/ids :as item}]
  (let [resp (http/POST *bom-url* scraper {:form-params (form-params item-ids)})]
    (if (= 200 (:status resp))
      (-> resp :body util/stream->html parse/bom)
      (throw (ex-info
              "failed to retrieve BOM"
              {:type ::fetch-bom
               :scraper scraper
               :item item
               :http-response resp})))))

(defn fetch-bom-pdf
  "Retrieves the Bill Of Materials report for an item in SV, returning an IO
  stream of the PDF file contents, `nil` if the item has no BOM, or throwing an
  error if the request fails."
  [scraper {item-ids :qad/ids :as item}]
  (let [params (form-params item-ids)
        resp (http/POST *report-url* scraper
               {:query-params (merge
                               {"action" "runB"
                                "oldAction" (str "/" *bom-url*)}
                               params)
                :form-params (merge
                              {"orientationB" "LANDSCAPE"}
                              params)})]
    (if (= 200 (:status resp))
      (:body resp)
      (throw (ex-info
              "failed to retrieve BOM PDF report"
              {:type ::fetch-bom-pdf
               :scraper scraper
               :item item
               :http-response resp})))))
