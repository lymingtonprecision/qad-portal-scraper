(ns qad-portal-scraper.filters
  (:require [net.cgrand.enlive-html :as html]
            [qad-portal-scraper.util :as util]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility fns

(defn first-value
  [inputs]
  (some-> inputs first :attrs :value))

(defn untyped-name
  [name]
  (re-find #"^[^:]+" name))

(defn row->filter
  "Extracts a filter definition from the given collection of filter table
  cells."
  [cells]
  (merge
   {:title (first (html/select cells [:th :span html/content]))
    :name (-> (html/select cells [:th :input]) first :attrs :value)}
   (if (= 2 (count cells))
     {:type :select
      :value (first-value (html/select cells [[:option (html/attr? :selected)]]))}
     {:type :text
      :value (first-value (html/select cells [[:input (html/attr= :type "text")]]))})))

(defn rows->filters
  "A transducer that will convert a collection of filter table cells into filter
  definitions."
  [xf]
  (let [field (volatile! [])]
    (fn
      ([] (xf))
      ([rs]
       (if-let [f (some-> @field seq row->filter)]
         (xf (xf rs f))
         (xf rs)))
      ([rs v]
       (when (= :th (:tag v))
         (if-let [f (some-> @field seq row->filter)]
           (do (vreset! field [])
               (xf rs f))
           rs))
       (do
         (vswap! field conj v)
         rs)))))

(defn index-by-name
  "Returns a sequence of filters as a may where the keys are the untyped filter
  names."
  [fs]
  (reduce
   (fn [rs f] (assoc rs (untyped-name (:name f)) f))
   {}
   fs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public

(defn from-page
  "Returns a map of all filters defined on the given page, keyed by the field
  name to which the filter applies."
  [page]
  (index-by-name
   (sequence
    rows->filters
    (html/select page [:#GridBorder :> :table.Detail :tr.alt #{:th :td}]))))

(defn unset
  "Returns a filter definition in it's \"unset\" state."
  [f]
  (assoc f :value nil))

(defn unset-all
  "Returns the given filter collection with every filter unset."
  [fs]
  (if (map? fs)
    (reduce
     (fn [rs [k v]] (assoc rs k (unset v)))
     {}
     fs)
    (map unset fs)))

(defn params
  "Returns the form parameters that should be submitted for the specified
  filters as part of a request in the specified context."
  [fs context]
  (let [fs (if (map? fs) (vals fs) (seq fs))
        [ctx op val] (reduce
                      (fn [[ctx op val] f]
                        [(conj ctx (:name f))
                         (conj op (if (= :text (:type f)) "=" (:value f)))
                         (conj val (if (= :text (:type f)) (:value f) ""))])
                      [[] [] []]
                      fs)]
    {(util/in-context "slc_CND_CTX" context) ctx
     (util/in-context "slc_CND_OP" context) op
     (util/in-context "slc_CND_VAL" context) val}))
