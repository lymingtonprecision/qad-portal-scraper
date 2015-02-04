(ns slb-sv-scraper.item
  (:require [clojure.string :as str]
            [slingshot.slingshot :refer [throw+]]
            [net.cgrand.enlive-html :as html]
            [slb-sv-scraper.session :as session]
            [slb-sv-scraper.util :as util]
            [slb-sv-scraper.bom-util :as bu]))

(def ^:dynamic *item-url* "sv/item.do")
(def ^:dynamic *bom-url* "sv/item/BOMReport.do")

(defn- org-id [page]
  (->> (html/select page [:#orgContext :a])
       first :attrs :href
       (re-find #"customerOrgSysID=(\d+)")
       second))

(defn get-item
  "Retrieves an items details from SV or throws an error if the request fails."
  [scraper item]
  (let [resp @(util/get *item-url* scraper
                        {:query-params (select-keys item ["itemPlaceSysID"
                                                          "supplierOrgSysID"
                                                          "poLineSysID"])})]
    (if (= 200 (:status resp))
      (-> (util/stream->html (:body resp)) org-id (->> (assoc item "orgSysID")))
      (throw+ {:type ::item-fetch
               :scraper scraper
               :item item
               :http-response resp}))))

(defn- bom-headers [page]
  (remove
    nil?
    (html/select page [:table.Grid :tr :span.detailHeaderBOM html/text-node])))

(defn- bom-items [page]
  (let [headers (bom-headers page)]
    (->> (html/select page [:table.Grid :tr])
         (drop 1)
         (map #(->> (html/select % [:td html/text-node])
                    (map (fn [s] (str/replace s #"\xa0" "")))
                    (zipmap headers))))))

(defn- bom-items->bom-tree [items]
  (assoc (first items) :components (bu/bom-table->bom-tree (rest items))))

(defn get-bom
  "Retrieves the Bill Of Materials for an item in SV, returning a nested map of
  the components or throwing an error if the request fails."
  [scraper item]
  (let [i (get-item scraper item)
        resp @(util/post *bom-url* scraper
                         {:form-params {"itemPlaceSysID" (get i "itemPlaceSysID")
                                        "entitySysID" (get i "itemPlaceSysID")
                                        "orgSysID" (get i "orgSysID")
                                        "level" "999"}})]
    (if (= 200 (:status resp))
      (-> resp :body util/stream->html bom-items bom-items->bom-tree)
      (throw+ {:type ::bom-fetch
               :scraper scraper
               :item item
               :http-response resp}))))
