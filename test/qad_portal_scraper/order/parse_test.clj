(ns qad-portal-scraper.order.parse-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [clojure.java.io :as io]
            [clj-time.coerce :as time.coerce]
            [schema.test]
            [qad-portal-scraper.test-utils :refer [html-resource]]
            [qad-portal-scraper.util :as util]
            [qad-portal-scraper.order.parse :as parse]))

(use-fixtures :once schema.test/validate-schemas)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; page-range

(deftest page-range-for-empty-order-results
  (let [page (html-resource "orders/invalid-order.htm")]
    (is (= [1] (vec (parse/page-range page))))))

(deftest page-range-for-single-page-results
  (let [page (html-resource "orders/multiline-order.htm")]
    (is (= [1] (vec (parse/page-range page))))))

(deftest page-range-for-multi-page-results
  (let [page1 (html-resource "orders/multipage.1.htm")
        page3 (html-resource "orders/multipage.3.htm")]
    (is (= [1 2 3 4] (vec (parse/page-range page1))))
    (is (= [1 2 3 4] (vec (parse/page-range page3))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; customer

(deftest customer-from-valid-order
  (let [page (html-resource "orders/singleline-order.htm")
        exp {:org/name "QAD-EPP3"
             :qad/ids {"customerOrgSysID" "7401"
                       "orgSysID" "7401"}}]
    (is (= exp (parse/customer page)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parse

(deftest parse-invalid-order
  (let [page (html-resource "orders/invalid-order.htm")]
    (is (nil? (seq (parse/orders page))))))

(deftest parse-single-page-order
  (let [page (html-resource "orders/singleline-order.htm")
        exp {"175408"
             {:order/id "175408"
              :qad/ids {"itemSysID" "881126"
                        "placeSysID" "2201"
                        "purchaseOrderHeaderSysID" "669269"
                        "supplierOrgSysID" "7218"}
              :order/customer {:org/name "QAD-EPP3"
                               :qad/ids {"customerOrgSysID" "7401"
                                         "orgSysID" "7401"}}
              :order/lines {1 {:order/id "175408"
                               :line/id 1
                               :qad/ids {"purchaseOrderLineSysID" "2547087"}
                               :line/item {:item/id "101030171"
                                           :qad/ids {"itemPlaceSysID" "407320"
                                                     "supplierOrgSysID" "7218"
                                                     "poLineSysID" "2547087"
                                                     "customerOrgSysID" "7401"
                                                     "orgSysID" "7401"}}
                               :line/item-revision "AA"
                               :line/uom "EA"
                               :line/quantity 6.0
                               :line/due-date (time.coerce/to-date "2016-03-23")}}}}]
    (is (= exp (parse/orders page)))))

(deftest parse-multi-page-order
  (let [pages [(html-resource "orders/multipage.1.htm")
               (html-resource "orders/multipage.2.htm")
               (html-resource "orders/multipage.3.htm")
               (html-resource "orders/multipage.4.htm")]
        orders (reduce #(parse/orders %2 %1) {} pages)]
    (is (= 1 (count orders)))
    (is (= 49 (count (-> orders vals first :order/lines))))))

(deftest parse-mutliple-orders
  (let [pages [(html-resource "orders/singleline-order.htm")
               (html-resource "orders/multiline-order.htm")]
        exp-line-counts {"175408" 1 "175356" 49}
        orders (reduce #(parse/orders %2 %1) {} pages)]
    (is (= 2 (count orders)))
    (is (= exp-line-counts
           (reduce
            (fn [rs [k v]]
              (assoc rs k (count (:order/lines v))))
            {}
            orders)))))
