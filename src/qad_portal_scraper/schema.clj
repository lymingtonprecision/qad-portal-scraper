(ns qad-portal-scraper.schema
  (:require [schema.core :as schema]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Elements

(def non-empty-str
  (schema/pred #(and (string? %) (not (clojure.string/blank? %)))
               'non-empty-str))

(def zero-or-pos-int
  (schema/pred #(and (integer? %) (>= % 0))
               'zero-or-pos-int))

(def pos-number
  (schema/pred #(and (number? %) (pos? %))
               'pos-number))

(def OrderID non-empty-str)
(def OrderLineID zero-or-pos-int)
(def ItemID non-empty-str)
(def ItemRevision (schema/maybe schema/Str))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Records

(def QadIds
  {non-empty-str schema/Str})

(def Org
  {:org/name non-empty-str
   :qad/ids QadIds})

(def Item
  {:item/id ItemID
   :qad/ids QadIds
   (schema/optional-key :item/description) non-empty-str
   (schema/optional-key :item/default-uom) non-empty-str
   (schema/optional-key :item/has-bom?) schema/Bool})

(def OrderLine
  {:order/id OrderID
   :line/id OrderLineID
   :qad/ids QadIds
   :line/item Item
   :line/item-revision ItemRevision
   :line/uom non-empty-str
   :line/quantity pos-number
   :line/due-date schema/Inst})

(def Order
  {:order/id OrderID
   :qad/ids QadIds
   :order/customer Org
   :order/lines {OrderLineID OrderLine}})

(def BOMEntry
  {:component/id ItemID
   :component/revision ItemRevision
   :component/description non-empty-str
   :component/qty pos-number
   :component/uom non-empty-str
   (schema/optional-key :bom/components) [(schema/recursive #'BOMEntry)]})

(def BOM
  {:bom/item Item
   :bom/revision ItemRevision
   :bom/date-issued schema/Inst
   :bom/components [BOMEntry]})
