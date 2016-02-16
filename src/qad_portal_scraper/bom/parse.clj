(ns qad-portal-scraper.bom.parse
  (:require [clojure.string :as string]
            [clj-time.coerce :as time.coerce]
            [net.cgrand.enlive-html :as html]
            [schema.core :as s]
            [qad-portal-scraper.schema :refer [BOM]]
            [qad-portal-scraper.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defn sanitize-level
  "Converts the QAD BOM level string (typically either \"parent\" or a number
  prefixed by periods) to the actual integer level of that level of the BOM."
  [l]
  (let [l (some-> l str string/lower-case)]
    (case l
      "parent" 0
      "....1" 10
      (try
        (Integer. (string/replace l #"\.+" ""))
        (catch java.lang.NumberFormatException e
          nil)))))

(defn raw-item->bom-entry
  "Returns a BOM entry record populated with the values from an entry scraped
  from the BOM report.

  The provided `i`tem should be a map from column headers to their values."
  [i]
  {:component/id (get i "Component Item")
   :component/revision (util/nil-if-blank (get i "Rev"))
   :component/description (get i "Description")
   :component/qty (some-> (get i "Quantity Per") Double.)
   :component/uom (get i "UM")
   :qad/flags {:level (sanitize-level (get i "Level"))
               :phantom (util/str->bool (get i "Phantom"))
               :ref-type (util/kebab-keyword (get i "Ref"))
               :issue? (util/str->bool (get i "Issue"))}})

(defn useable?
  "Returns truthy if the provided BOM entry represents an actual item that would
  be used in the construction of the part."
  [c]
  (and (pos? (:component/qty c))
       (= :uses (get-in c [:qad/flags :ref-type]))))

(defn item-headers
  "Returns the BOM report table header strings."
  [page]
  (remove
   nil?
   (html/select page [:table.Grid :tr :span.detailHeaderBOM html/text-node])))

(defn item-list
  "Returns a collection of the rows from the BOM report, in the same order in
  which they appear on the report, parsed as BOM entry records."
  [page]
  (let [headers (item-headers page)]
    (->> (html/select page [:table.Grid :tr])
         (drop 1)
         (map #(->> (html/select % [:td html/text-node])
                    (map (fn [s] (-> (util/remove-nbsp s) string/trim util/nil-if-blank)))
                    (zipmap headers)
                    raw-item->bom-entry)))))

(defn list->tree
  "Given a list of raw BOM entry records as produced by [[item-list]] returns the
  corresponding nested BOM structure."
  [items]
  (let [skip? (volatile! (constantly false))]
    (reduce
     (fn [t i]
       (let [lvl (get-in i [:qad/flags :level])
             path (interleave (repeat (dec lvl) 0) (repeat :bom/components))
             c (dissoc i :qad/flags)]
         (cond
           (@skip? lvl) t
           (not (useable? i))
           (do
             (vreset! skip? #(> % lvl))
             t)
           :else
           (do
             (vreset! skip? (constantly false))
             (if (seq path)
               (update-in t path #(vec (cons c %)))
               (vec (cons c t)))))))
     []
     items)))

(defn bom-header
  "Returns a map of the information contained in the BOM report header."
  [page]
  (reduce
   (fn [rs r]
     (let [k (some-> (html/select r [:th html/content])
                     first
                     (string/replace  #":$" ""))
           v (first (html/select r [:td html/content]))]
       (assoc rs k v)))
   {}
   (html/select page [:table.Detail :tr.alt])))

(defn bom-ids
  "Returns a map of the BOM/item IDS encoded in the BOM report."
  [page]
  (reduce
   (fn [rs i]
     (let [a (:attrs i)]
       (if (= "itemID" (:name a))
         rs
         (assoc rs (:name a) (:value a)))))
   {}
   (html/select page [:#MainBody :> :form :> [:input (html/attr= :type "hidden")]])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(s/defn bom :- (s/maybe BOM)
  "Returns the fully parsed BOM structure from the given BOM report page."
  [page]
  (let [header (bom-header page)
        [parent & components] (item-list page)]
    (when (not (string/blank? (:component/id parent)))
      {:bom/item {:item/id (:component/id parent)
                  :qad/ids (bom-ids page)
                  :item/description (:component/description parent)
                  :item/default-uom (:component/uom parent)
                  :item/has-bom? (if (seq components) true false)}
       :bom/revision (:component/revision parent)
       :bom/date-issued (time.coerce/to-date (get header "Date"))
       :bom/components (list->tree components)})))
