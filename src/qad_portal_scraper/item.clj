(ns qad-portal-scraper.item
  (:require [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [net.cgrand.enlive-html :as html]
            [schema.core :as s]
            [qad-portal-scraper.schema :refer [Item]]
            [qad-portal-scraper.http :as http]
            [qad-portal-scraper.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Default URL Paths

(def ^:dynamic *item-url* "sv/item.do")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defn item-details-table [page]
  (first (html/select page [:.NestedDetail :table.Detail])))

(defn map-item-rows [table]
  (reduce
   (fn [rs tr]
     (let [th (-> tr (html/select [:th]) first :content first)
           td (-> tr (html/select [:td]) first :content)
           label (string/replace th #":$" "")]
       (assoc rs label td)))
   {}
   (html/select table [:tr])))

(defn item-org-ids [org-cell]
  (-> (html/select org-cell [:a])
      first
      :attrs
      :href
      util/link-params
      (rename-keys {"pOrgSysID" "orgSysID"})))

(defn sanitize-keys-and-values [item]
  (reduce
   (fn [rs [k v]]
     (case k
       "Item ID" (assoc rs :item/id (first v))
       "Organization" (assoc rs :qad/ids (item-org-ids v))
       "Description" (assoc rs :item/description (first v))
       "UM" (assoc rs :item/default-uom (first v))
       "BOM Report" (assoc rs :item/has-bom? (some? v))
       rs))
   {}
   item))

(defn parse [page]
  (some-> page
          item-details-table
          map-item-rows
          not-empty
          sanitize-keys-and-values))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(s/defn fetch-item :- (s/maybe Item)
  "Retrieves an items details from SV or throws an error if the request fails.

  The returned map of item details will include a `:has-bom?` key that will be
  `true` when the items details indicate it has a BOM available via SV and false
  if no BOM is available."
  ([scraper
    {{item-id "itemPlaceSysID" org-id "supplierOrgSysID"} :qad/ids :as item}]
   (fetch-item scraper item-id org-id))
  ([scraper item-id org-id]
   (let [resp (http/GET *item-url* scraper
                {:query-params {"itemPlaceSysID" item-id
                                "supplierOrgSysID" org-id}})]
     (if (= 200 (:status resp))
       (some-> (parse (util/stream->html (:body resp)))
               (update :qad/ids merge {"itemPlaceSysID" item-id "supplierOrgSysID" org-id}))
       (throw (ex-info
               "failed to retrieve item details"
               {:type ::fetch-item
                :scraper scraper
                :item-id item-id
                :supplier-org-id org-id
                :http-response resp}))))))
