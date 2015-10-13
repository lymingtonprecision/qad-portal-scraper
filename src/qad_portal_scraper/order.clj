(ns qad-portal-scraper.order
  (:require [slingshot.slingshot :refer [throw+]]
            [org.httpkit.client :as http]
            [net.cgrand.enlive-html :as html]
            [qad-portal-scraper.util :as util]))

(def ^:dynamic *orders-url* "sv/orders/list-orders.do")

(def order-columns
  ["OrderNumber"
   "OrderLine"
   "ItemID"
   "ItemRevision"
   "UM"
   "OrderQuantity"
   "DueDate"])

(defn extract-context-key-from-body [body]
  (let [res (html/html-resource body)
        k (html/select res [[:input (html/attr-ends :name "_KEY")]])
        km (reduce (fn [m {{n :name v :value} :attrs}] (assoc m n v)) {} k)]
    {:context (get km "slc_CONTEXT_KEY") :key (get km "slc_KEY")}))

(defn get-order-context-key
  "Retrieves the current order context key from SV for the session
  established by the scraper. Throws an error if the request fails."
  [scraper]
  (let [resp @(util/get *orders-url* scraper)
        b (util/stream->html (:body resp))]
    (if (= 200 (:status resp))
      (extract-context-key-from-body b)
      (throw+ {:type ::context-fetch
               :scraper scraper
               :http-response resp}))))

(defn- fp  [c n] (str n "_" (:key c)))

(defn form-params
  ([order-no context] (form-params order-no context 1))
  ([order-no context page]
   {"slc_KEY" (:key context)
    "slc_CONTEXT_KEY" (:context context)
    "activeTab" "data"
    "lastTab" "data"
    "lastTabSupport" "filter"
    (fp context "slc_CND_ACTION") ""
    (fp context "slc_PAGE") (max 0 (dec page)) ; page numbers are zero-indexed in SV
    (fp context "slc_PAGE_SIZE") "100"} ))

(defn filter-form-params [order-no context]
  (merge (form-params order-no context)
         {"applyAtts" "true"
          (fp context "slc_ATT_REORDER") order-columns
          (fp context "slc_CND_ACTION") "replace"
          (fp context "slc_CND_CTX") "OrderNumber:java.lang.String"
          (fp context "slc_CND_OP") "="
          (fp context "slc_CND_VAL") order-no}))

(defn- attribute-names [page]
  (map #(->> % :attrs :href (re-find #"value='6:([A-Za-z]+)';") second)
       (html/select page [:#AttributeNames :th :a])))

(defn- page-range [page]
  (-> (html/select page [:#GridNavigation])
      first
      (html/select [#{:button.PaginationInContext :button.Pagination}
                    (html/text-pred #(re-matches #"\d+" %))])
      (->> (map #(java.lang.Integer. %)) sort)))

(defn- link-params [href]
  (if href
    (->> href
         (re-seq #"setParam\(document\.getElementById\('fmain'\),'([A-Za-z]+)','([^']+)'\);")
         (reduce (fn [r [_ k v]] (assoc r k v)) {}))))

(defn- cell->value [c]
  (let [content (-> c :content first)
        text (clojure.string/join (html/select content [html/text-node]))
        lp (if (associative? content)
             (link-params (-> (html/select c [:a]) first :attrs :href)))]
    (merge {:value text} lp)))

(defn- order-lines [page column-names]
  (map #(->> % :content rest butlast
             (map cell->value)
             (zipmap column-names))
       (html/select page [:table.Grid :tr.alt])))

(defn- all-items-unique-and-from-order? [order-no items]
  (if (seq items)
    (let [on (distinct (map #(-> % (get "OrderNumber") :value) items))
          lc (count items)
          ln (distinct (map #(-> % (get "OrderLine") :value java.lang.Integer.)
                            items))]
      (and (= on [order-no])
           (= (count ln) lc)))
    true))

(defn- get-order-page [scraper context order-no page]
  (let [params (if (= page 1)
                 (filter-form-params order-no context)
                 (form-params order-no context page))
        req (util/post *orders-url* scraper {:form-params params})]
    #(let [resp @req]
       (if (= 200 (:status resp))
         resp
         (throw+ {:type ::order-fetch
                  :http-response resp
                  :scraper scraper
                  :context context
                  :order-no order-no
                  :page page})))))

(defn get-order
  "Retrieves the specified order details from SV, returning a collection of the
  order lines. Throws an error if a request fails."
  [scraper order-no]
  {:post [(all-items-unique-and-from-order? order-no %)]}
  (let [c (get-order-context-key scraper)
        get-page (partial get-order-page scraper c order-no)
        first-page (get-page 1)
        b (util/stream->html (:body (first-page)))
        h (attribute-names b)
        pages (map #(if (= % 1) first-page (get-page %)) (page-range b))]
    (reduce
      (fn [r page]
        (concat r (order-lines (util/stream->html (:body (page))) h)))
      []
      (if (seq pages) pages [first-page]))))
