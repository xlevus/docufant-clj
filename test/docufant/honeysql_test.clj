(ns docufant.honeysql-test
  (:require [docufant.honeysql :as dhoney :refer [jsonb-path]]
            [docufant.postgres :as pg]
            [honeysql.core :as sql]
            [clojure.test :as t :refer [deftest testing is]]))

(defn where [& clause]
  (sql/format {:where clause}))


(deftest test-honeysql-fn-handler
  (testing "="
    (is (= ["WHERE (data) = ?" (pg/jsonb 1)]
           (where "jsonb=" (jsonb-path :data) 1)))

    (is (= ["WHERE (data -> ?) = ?" "a" (pg/jsonb 1)]
           (where "jsonb=" (jsonb-path :data [:a]) 1)))

    (is (= ["WHERE (data #> ?) = ?" (pg/text-array [:a :b]) (pg/jsonb 1)]
           (where "jsonb=" (jsonb-path :data [:a :b]) 1)))

    ))


(deftest test-honeysql-fn-handler-casts
  (testing "="
    (is (= ["WHERE (data)::int = ?" 1]
           (where "jsonb=" (jsonb-path :data nil Long) 1)))

    (is (= ["WHERE (data ->> ?)::int = ?" "a" 1]
           (where "jsonb=" (jsonb-path :data [:a] Long) 1)))

    (is (= ["WHERE (data #>> ?)::int = ?" (pg/text-array [:a :b]) 1]
           (where "jsonb=" (jsonb-path :data [:a :b] Long) 1)))

    ))


(deftest test-honeysql-create-index
  (is (= ["CREATE INDEX IF NOT EXISTS indexname ON table (field)"]
      (sql/format {:create-index {:name :indexname
                                  :on :table
                                  :field :field}})))

  (is (= ["CREATE INDEX indexname ON table (field) WHERE (data ->> ?)::int = ?"
          "a" 1]
         (sql/format {:create-index {:name :indexname
                                     :force true
                                     :on :table
                                     :field :field}
                      :where ["jsonb=" (jsonb-path :data [:a] Long) 1]})))

  (is (= ["CREATE INDEX indexname ON table ((data #> ?))"
          (pg/text-array [:a :b])]
         (sql/format {:create-index {:name :indexname
                                     :force true
                                     :on :table
                                     :field (jsonb-path :data [:a :b])}})))

  (is (= ["CREATE INDEX IF NOT EXISTS indexname ON table USING gin(field)"]
         (sql/format {:create-index {:name :indexname
                                     :on :table
                                     :using (sql/raw "gin(field)")}})))

  )
