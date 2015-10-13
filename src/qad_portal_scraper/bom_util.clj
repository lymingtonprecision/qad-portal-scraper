(ns qad-portal-scraper.bom-util
  (:require [clojure.string :as str])
  (:import [java.lang Integer Double]))

(def useable-component-type "USES")

(defn component-is-useable? [c]
  (or (= (get c "Level") "Parent")
      (and (pos? (Double. (get c "Quantity Per" "0")))
           (= useable-component-type (get c "Ref")))))

(defn- replace-bogus-levels [l]
  (if (= l "....1") "10" l))

(defn bom-table->bom-tree
  "Converts a table of BOM entries, as scraped from an SV BOM Report,
  into a nested map of: `{... :components [{...} {...}]}`"
  ([bom]
   (bom-table->bom-tree
     bom
     #(-> (get % "Level")
          replace-bogus-levels
          (str/replace #"\.+" "")
          (Integer.))))
  ([bom lvl-fn]
   (reduce
     (fn [t c]
       (let [l (lvl-fn c)]
         (if (= l 1)
           (vec (cons c t))
           (update-in t
                      (interleave (repeat (dec l) 0) (repeat :components))
                      #(vec (cons c %))))))
     []
     bom)))

(defn bom-tree-walker [cons-fn branch-fn component-fn result-fn]
  (fn [bom]
    (let [walk (fn walk [depth node]
                 (lazy-seq
                   (cons (cons-fn node depth)
                         (when (branch-fn node)
                           (mapcat (partial walk (inc depth))
                                   (component-fn node))))))]
      (result-fn (walk 0 bom)))))

(def bom-tree-depth
  "Returns the maximum branch depth of a BOM"
  (bom-tree-walker
    (fn [_ d] d)
    :components
    :components
    #(apply max %)))

(defn filter-by-useable
  "Returns a BOM tree with all the non-useable components removed"
  [b]
  (let [u? (component-is-useable? b)
        uc (if u? (remove nil? (map filter-by-useable (:components b))))
        ub (if (seq uc) (assoc b :components uc) (dissoc b :components))]
    (if u? ub)))
