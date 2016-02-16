(ns qad-portal-scraper.order.parse
  (:require [clj-time.coerce :as time.coerce]
            [net.cgrand.enlive-html :as html]
            [schema.core :as s]
            [qad-portal-scraper.schema :refer :all]
            [qad-portal-scraper.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Constants

(def order-attribute->record-key
  {"OrderNumber" :order/id
   "OrderLine" :line/id
   "ItemID" :item/id
   "ItemRevision" :line/item-revision
   "ItemDescription" :item/description
   "UM" :line/uom
   "OrderQuantity" :line/quantity
   "DueDate" :line/due-date})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Value coercion

(defmulti coerce-value (fn [v k] k))

(defmethod coerce-value :default [v _] v)

(defmethod coerce-value :line/id [v _]
  (when v
    (java.lang.Integer.
     (clojure.string/replace v #"\.\d+$" ""))))

(defmethod coerce-value :line/quantity [v _] (when v (java.lang.Double. v)))
(defmethod coerce-value :line/due-date [v _] (time.coerce/to-date v))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing

(defn link-params-for-cell
  "Returns the portal navigation link parameters for the value contained within
  the given data table cell, if any."
  [td]
  (util/link-params (-> (html/select td [:a]) first :attrs :href)))

(defn remove-ui-columns
  "Returns the row with columns only used to display UI elements removed."
  [row]
  (->> row :content rest butlast))

(defn update-line
  "Updates the provided map of order line details with the value of the provided
  table cell, under key `k`."
  [line k td]
  (let [content (first (:content td))
        value (-> (html/select content [html/text-node])
                  clojure.string/join
                  (coerce-value k))
        path (if (= (name :item) (namespace k))
               [:line/item k]
               [k])
        ids (when (associative? content)
              (link-params-for-cell td))
        id-path (conj (vec (butlast path)) :qad/ids)]
    (cond-> line
      true (assoc-in path value)
      ids (assoc-in id-path ids))))

(s/defn row->order-line :- OrderLine
  "Returns an order line map populated from the given collection of table cells.

  `cell-map` should be a map of the keys to use in the produced map to the table
  cells from which to extract the corresponding value."
  [cell-map]
  (reduce
   (fn [rs [k td]]
     (update-line rs k td))
   {}
   cell-map))

(s/defn merge-line-into-orders :- {OrderID Order}
  "Merges the given `line` into the provided map of `orders`, initializing the
  entry for the order with `init-order` if it is missing."
  [line :- OrderLine orders :- {OrderID Order} init-order :- Order]
  (if (contains? orders (:order/id line))
    (assoc-in
     orders
     [(:order/id line) :order/lines (:line/id line)]
     line)
    (assoc
     orders
     (:order/id line)
     (assoc-in init-order [:order/lines (:line/id line)] line))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn page-range
  "Returns an ordered sequence of result page numbers from the given parsed
  `page`.

  Returns a single page sequence, `[1]`, if the page includes no links to
  additional pages."
  [page]
  (-> (html/select page [:#GridNavigation])
      first
      (html/select [#{:button.PaginationInContext :button.Pagination}
                    (html/text-pred #(re-matches #"\d+" %))])
      (->> (map #(java.lang.Integer. %)))
      seq
      (or [1])
      sort))

(defn column-headings
  "Returns an ordered collection of the column headings for entries in the data
  table on `page`."
  [page]
  (map #(->> % :attrs :href (re-find #"value='6:([A-Za-z]+)';") second)
       (html/select page [:#AttributeNames :th :a])))

(s/defn customer :- (s/maybe Org)
  "Returns an organization record for the customer to which the given page
  refers."
  [page]
  (when-let [link (first (html/select page [:#orgContext :a]))]
    {:org/name (first (:content link))
     :qad/ids (reduce
               (fn [rs [_ k v]] (assoc rs (if (= k "pOrgSysID") "orgSysID" k) v))
               {}
               (re-seq #"(?i)&([a-z]+)=(\d+)" (-> link :attrs :href)))}))

(s/defn orders :- {OrderID Order}
  "Returns a map of the orders present on `page`."
  ([page]
   (orders page {} (column-headings page)))
  ([page order-map]
   (orders page order-map (column-headings page)))
  ([page order-map column-names]
   (let [ks (map (fn [cn] (get order-attribute->record-key cn cn)) column-names)
         org (customer page)
         rows (map
               (comp (partial zipmap ks) remove-ui-columns)
               (html/select page [:table.Grid :tr.alt]))]
     (reduce
      (fn [rs row]
        (let [line (-> (row->order-line row)
                       (update-in
                        [:line/item :qad/ids]
                        merge
                        (:qad/ids org)))]
          (merge-line-into-orders
           line
           rs
           {:order/id (:order/id line)
            :qad/ids (link-params-for-cell (:order/id row))
            :order/customer org
            :order/lines {}})))
      (or order-map {})
      rows))))
