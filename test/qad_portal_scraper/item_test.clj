(ns qad-portal-scraper.item-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [schema.test]
            [qad-portal-scraper.test-utils :refer [html-resource]]
            [qad-portal-scraper.item :as item]))

(use-fixtures :once schema.test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parse

(deftest parse-invalid-item
  (let [page (html-resource "items/invalid-item.htm")]
    (is (nil? (item/parse page)))))

(deftest parse-valid-item
  (let [page (html-resource "items/simple-item.htm")
        exp {:item/id "100504830"
             :qad/ids {"customerOrgSysID" "7401" "orgSysID" "7401"}
             :item/description "Mauris mollis tincidunt felis"
             :item/default-uom "EA"
             :item/has-bom? true}]
    (is (= exp (item/parse page)))))

(deftest parse-item-without-bom
  (let [page (html-resource "items/item-without-bom.htm")
        exp {:item/id "101030171"
             :qad/ids {"customerOrgSysID" "7401" "orgSysID" "7401"}
             :item/description "Donec pretium posuere tellus"
             :item/default-uom "EA"
             :item/has-bom? false}]
    (is (= exp (item/parse page)))))
