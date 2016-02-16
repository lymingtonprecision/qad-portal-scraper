(ns qad-portal-scraper.filters-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [net.cgrand.enlive-html :as html]
            [qad-portal-scraper.filters :as filters]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures

(def text-filter
  "<th>
     <span>UM</span>
     <input name=\"slc_CND_CTX_12345a\" type=\"hidden\" value=\"UM:java.lang.String\"></input>
   </th>
   <td>
     <select name=\"slc_CND_OP_12345a\">
       <option value=\"=\">=</option>
       <option value=\"<>\">&lt;&gt;</option>
       <option value=\"LIKE\">Like</option>
       <option value=\">\">&gt;</option>
       <option value=\">=\">&gt;=</option>
       <option value=\"<\">&lt;</option>
       <option value=\"<=\">&lt;=</option>
       <option valueIN\"IN\">In</option>
     </select>
   </td>
   <td nowrap=\"\">
     <input name=\"slc_CND_VAL_12345a\" class=\"TextField\" type=\"text\" value=\"EA\"></input>
   </td>")

(def select-filter
  "<th>
     <span>Alert Notifications</span>
     <input name=\"slc_CND_CTX_12345a\" type=\"hidden\" value=\"NotificationCount:java.math.BigInteger\"></input>
   </th>
   <td colspan=\"2\">
     <select name=\"slc_CND_OP_12345a\">
       <option value=\"\"></option>
       <option value=\"NotificationCount.HasNotifications\" selected=\"selected\">Notifications</option>
       <option value=\"NotificationCount.DoesNotHasNotifications\">No Notifications</option>
     </select>
     <input name=\"slc_CND_VAL_12345a\" type=\"hidden\"></input>
   </td>")

(def field-row
  (html/html-snippet
   (str "<tr class=\"alt\">"
        text-filter
        select-filter
        "</tr>")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(deftest index-by-name
  (let [cells (html/select field-row [#{:th :td}])
        exp {"UM"
             {:title "UM"
              :type :text
              :name "UM:java.lang.String"
              :value "EA"}
             "NotificationCount"
             {:title "Alert Notifications"
              :type :select
              :name "NotificationCount:java.math.BigInteger"
              :value "NotificationCount.HasNotifications"}}]
    (is (= exp (filters/index-by-name (into [] filters/rows->filters cells))))))

(deftest unset-all
  (let [cells (html/select field-row [#{:th :td}])
        fs {"UM"
            {:title "UM"
             :type :text
             :name "UM:java.lang.String"
             :value nil}
            "NotificationCount"
            {:title "Alert Notifications"
             :type :select
             :name "NotificationCount:java.math.BigInteger"
             :value nil}}
        exp (-> fs
                (assoc-in ["UM" :value] nil)
                (assoc-in ["NotificationCount" :value] nil))]
    (is (= exp (filters/unset-all fs)))))

(deftest params
  (let [fs [{:title "Item"
             :name "ItemID:java.lang.String"
             :type :text
             :value ""}
            {:title "Alert Notifications"
             :type :select
             :name "NotificationCount:java.math.BigInteger"
             :value "NotificationCount.HasNotifications"}
            {:title "UM"
             :type :text
             :name "UM:java.lang.String"
             :value "EA"}]
        fs-map (apply sorted-map (reduce #(conj %1 (:name %2) %2) [] fs))
        context-key "12345a"
        exp {(str "slc_CND_CTX_" context-key)
             ["ItemID:java.lang.String"
              "NotificationCount:java.math.BigInteger"
              "UM:java.lang.String"]
             (str "slc_CND_OP_" context-key)
             ["="
              "NotificationCount.HasNotifications"
              "="]
             (str "slc_CND_VAL_" context-key)
             ["" "" "EA"]}]
    (is (= exp (filters/params fs {:key context-key})))
    (is (= exp (filters/params fs-map {:key context-key})))))
