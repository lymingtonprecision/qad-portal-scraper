(ns qad-portal-scraper.bom.parse-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.zip :as zip]
            [clj-time.coerce :as time.coerce]
            [schema.test]
            [qad-portal-scraper.test-utils :refer [html-resource]]
            [qad-portal-scraper.bom.parse :as parse]))

(use-fixtures :once schema.test/validate-schemas)

(deftest invalid-item
  (let [page (html-resource "boms/invalid-item.htm")]
    (is (nil? (parse/bom page)))))

(deftest item-without-bom
  (let [page (html-resource "boms/item-without-bom.htm")
        exp {:bom/item {:item/id "101030171OUTER SKIN 35KSI CONTROLUNIT ANTENNA"
                        :item/description "OUTER SKIN 35KSI CONTROLUNIT ANTENNA"
                        :qad/ids {"orgSysID" "7401"
                                  "itemPlaceSysID" "407320"
                                  "entitySysID" "407320"}
                        :item/default-uom "EA"
                        :item/has-bom? false}
             :bom/revision nil
             :bom/date-issued (time.coerce/to-date "2016-02-12")
             :bom/components []}]
    (is (= exp (parse/bom page)))))

(deftest single-level-bom
  (let [page (html-resource "boms/single-level.htm")
        exp {:bom/item {:item/id "100302863"
                        :item/description "Mauris mollis tincidunt felis"
                        :qad/ids {"orgSysID" "7401"
                                  "itemPlaceSysID" "163922"
                                  "entitySysID" "163922"}
                        :item/default-uom "EA"
                        :item/has-bom? true}
             :bom/revision "AC"
             :bom/date-issued (time.coerce/to-date "2016-02-08")
             :bom/components [{:component/id "S-248807"
                               :component/description "Lorem ipsum dolor sit amet"
                               :component/revision "AK"
                               :component/qty 2.0
                               :component/uom "EA"}]}]
    (is (= exp (parse/bom page)))))

(deftest multi-level-bom
  (let [page (html-resource "boms/10-levels.htm")
        bom (parse/bom page)
        node-id (fn [n]
                  (or (get-in n [:bom/item :item/id])
                      (get-in n [:component/id])))
        zp (zip/zipper map? :bom/components (fn [n _] n) bom)
        tree (loop [zp zp t {}]
               (if (zip/end? zp)
                 t
                 (let [t (update-in
                          t
                          (conj
                           (vec (map node-id (zip/path zp)))
                           (node-id (zip/node zp)))
                          #(or % {}))]
                   (recur (zip/next zp) t))))
        exp {"100440129"
             {"100181298" {}
              "100232779" {}
              "100243202" {}
              "100263620" {}
              "100272680" {}
              "100272692" {}
              "100284099" {}
              "100353424" {}
              "100353889" {}
              "100440125" {"100535574"
                           {"100533792"
                            {"100440126"
                             {"100440127"
                              {"100440128"
                               {"100440131"
                                {"100456176" {}
                                 "100456179" {}
                                 "100747907" {"100455911" {}}}
                                "100540823" {}
                                "100747330" {"100747351" {} "100831253" {}}
                                "100747438" {"100747459" {} "100831253" {}}
                                "D2441" {}
                                "D2831" {}}}}
                             "100535967" {}}
                            "D5062" {}}}
              "100444430" {"100319026" {}
                           "100902061" {"100444431" {"100386548" {}}}
                           "100902075" {}}
              "100451187" {}
              "100513598" {}
              "101573198" {}
              "8004" {}
              "8071" {}
              "8072" {}
              "8384" {}
              "B025479" {}
              "B043619" {}
              "B044982" {}
              "B046587" {}
              "E027357" {}
              "K-140625" {}}}]
  (is (= exp tree))))
